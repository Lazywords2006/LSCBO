#include "lscbo/algorithms.hpp"

#include "common.hpp"

#include <cmath>
#include <cstddef>
#include <limits>
#include <memory>
#include <vector>

namespace lscbo {
namespace {

constexpr std::size_t population_size = 30;
constexpr std::size_t pair_count = population_size / 2;
constexpr double pi = 3.141592653589793238462643383279502884;

class cbo_optimizer final : public optimizer {
public:
    cbo_optimizer(bool levy_enabled, algorithm_configuration configuration)
        : levy_enabled_{levy_enabled}, configuration_{configuration} {}

    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(
            problem, population_size, random, tracker);
        if (state.positions.empty()) return tracker.result(problem);

        while (problem.remaining() > 0) {
            const double phase_progress = detail::progress(problem);
            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate;
                if (phase_progress < 1.0 / 3.0) {
                    candidate = searching_candidate(state, index, random);
                } else if (phase_progress < 2.0 / 3.0) {
                    candidate = encircling_candidate(state, index, phase_progress, random);
                } else {
                    candidate = attacking_candidate(state, index);
                }
                detail::clamp(candidate, problem);
                const double value = tracker.evaluate(problem, candidate);
                if (value <= state.fitness[index]) {
                    state.positions[index] = candidate;
                    state.fitness[index] = value;
                }

                if (levy_enabled_ && phase_progress < 1.0 / 3.0 &&
                    problem.remaining() > 0 &&
                    random.uniform() < configuration_.lscbo_levy_fraction) {
                    detail::position levy = state.positions[index];
                    const double decay = std::max(0.05, 1.0 - detail::progress(problem));
                    const double range = problem.upper_bound() - problem.lower_bound();
                    for (double& coordinate : levy) {
                        coordinate += configuration_.lscbo_levy_scale * range * decay *
                                      levy_step(random, configuration_.lscbo_levy_beta);
                    }
                    detail::clamp(levy, problem);
                    const double levy_value = tracker.evaluate(problem, levy);
                    if (levy_value <= state.fitness[index]) {
                        state.positions[index] = std::move(levy);
                        state.fitness[index] = levy_value;
                    }
                }
            }
        }
        return tracker.result(problem);
    }

private:
    static detail::position searching_candidate(const detail::population& state,
                                                std::size_t index,
                                                detail::random_source& random) {
        const std::size_t pair = index % pair_count;
        const std::size_t coyote = pair;
        const std::size_t badger = std::min(pair + pair_count,
                                            state.positions.size() - 1);
        detail::position candidate = state.positions[index];
        for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
            const double prey = 0.5 * (state.positions[coyote][dimension] +
                                       state.positions[badger][dimension]);
            const double sense = random.uniform(-0.5, 0.5);
            candidate[dimension] *= 1.0 + sense * std::tanh(prey);
        }
        return candidate;
    }

    static detail::position encircling_candidate(const detail::population& state,
                                                 std::size_t index, double progress,
                                                 detail::random_source& random) {
        const std::size_t pair = index % pair_count;
        const std::size_t coyote = pair;
        const std::size_t badger = std::min(pair + pair_count,
                                            state.positions.size() - 1);
        detail::position candidate = state.positions[index];
        const std::size_t first = random.index(candidate.size());
        std::size_t second = random.index(candidate.size());
        while (second == first) second = random.index(candidate.size());
        const double local = (progress - 1.0 / 3.0) * 3.0;
        const double angle = pi * (1.0 - std::min(1.0, local));
        const double cosine = std::cos(angle);
        const double sine = std::sin(angle);
        const double prey_first = 0.5 * (state.positions[coyote][first] +
                                         state.positions[badger][first]);
        const double prey_second = 0.5 * (state.positions[coyote][second] +
                                          state.positions[badger][second]);
        const double offset_first = candidate[first] - prey_first;
        const double offset_second = candidate[second] - prey_second;
        candidate[first] = prey_first + cosine * offset_first - sine * offset_second;
        candidate[second] = prey_second + sine * offset_first + cosine * offset_second;
        return candidate;
    }

    static detail::position attacking_candidate(const detail::population& state,
                                                std::size_t index) {
        const std::size_t split = std::min(pair_count, state.positions.size());
        std::size_t leader = 0;
        if (index < split) {
            leader = static_cast<std::size_t>(std::distance(
                state.fitness.begin(),
                std::min_element(state.fitness.begin(), state.fitness.begin() + split)));
        } else {
            leader = split + static_cast<std::size_t>(std::distance(
                state.fitness.begin() + split,
                std::min_element(state.fitness.begin() + split, state.fitness.end())));
        }
        detail::position candidate = state.positions[index];
        for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
            candidate[dimension] = 0.5 * (candidate[dimension] +
                                          state.positions[leader][dimension]);
        }
        return candidate;
    }

    static double levy_step(detail::random_source& random, double beta) {
        const double numerator = std::tgamma(1.0 + beta) *
                                 std::sin(pi * beta / 2.0);
        const double denominator = std::tgamma((1.0 + beta) / 2.0) *
                                   beta * std::pow(2.0, (beta - 1.0) / 2.0);
        const double sigma = std::pow(numerator / denominator, 1.0 / beta);
        const double u = sigma * random.normal();
        const double v = std::max(1e-12, std::abs(random.normal()));
        return u / std::pow(v, 1.0 / beta);
    }

    bool levy_enabled_;
    algorithm_configuration configuration_;
};

}  // namespace

std::unique_ptr<optimizer> make_cbo_optimizer() {
    return std::make_unique<cbo_optimizer>(false, algorithm_configuration{});
}

std::unique_ptr<optimizer> make_lscbo_optimizer(
    const algorithm_configuration& configuration) {
    return std::make_unique<cbo_optimizer>(true, configuration);
}

}  // namespace lscbo
