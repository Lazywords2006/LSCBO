#include "lscbo/algorithms.hpp"

#include "common.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <limits>
#include <memory>
#include <numeric>
#include <vector>

namespace lscbo {
namespace {

constexpr std::size_t population_size = 30;
constexpr double pi = 3.141592653589793238462643383279502884;

class pso_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(problem, population_size, random, tracker);
        const double range = problem.upper_bound() - problem.lower_bound();
        std::vector<detail::position> velocity(
            state.positions.size(), detail::position(problem.dimensions(), 0.0));
        for (auto& vector : velocity) {
            for (double& value : vector) value = random.uniform(-0.1 * range, 0.1 * range);
        }
        auto personal_best = state.positions;
        auto personal_fitness = state.fitness;

        while (problem.remaining() > 0) {
            const double inertia = 0.9 - 0.5 * detail::progress(problem);
            const detail::position global_best = tracker.best_position();
            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate = state.positions[index];
                for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                    velocity[index][dimension] =
                        inertia * velocity[index][dimension] +
                        2.0 * random.uniform() *
                            (personal_best[index][dimension] - candidate[dimension]) +
                        2.0 * random.uniform() *
                            (global_best[dimension] - candidate[dimension]);
                    velocity[index][dimension] = std::max(
                        -0.2 * range, std::min(0.2 * range, velocity[index][dimension]));
                    candidate[dimension] += velocity[index][dimension];
                }
                detail::clamp(candidate, problem);
                const double value = tracker.evaluate(problem, candidate);
                state.positions[index] = candidate;
                state.fitness[index] = value;
                if (value <= personal_fitness[index]) {
                    personal_best[index] = std::move(candidate);
                    personal_fitness[index] = value;
                }
            }
        }
        return tracker.result(problem);
    }
};

class gwo_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(problem, population_size, random, tracker);

        while (problem.remaining() > 0) {
            std::vector<std::size_t> order(state.positions.size());
            std::iota(order.begin(), order.end(), 0);
            std::stable_sort(order.begin(), order.end(), [&](std::size_t left, std::size_t right) {
                return state.fitness[left] < state.fitness[right];
            });
            const auto& alpha = state.positions[order[0]];
            const auto& beta = state.positions[order[std::min<std::size_t>(1, order.size() - 1)]];
            const auto& delta = state.positions[order[std::min<std::size_t>(2, order.size() - 1)]];
            const double a = 2.0 * (1.0 - detail::progress(problem));

            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate = state.positions[index];
                for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                    candidate[dimension] =
                        (leader_component(alpha[dimension], state.positions[index][dimension], a, random) +
                         leader_component(beta[dimension], state.positions[index][dimension], a, random) +
                         leader_component(delta[dimension], state.positions[index][dimension], a, random)) /
                        3.0;
                }
                detail::clamp(candidate, problem);
                state.fitness[index] = tracker.evaluate(problem, candidate);
                state.positions[index] = std::move(candidate);
            }
        }
        return tracker.result(problem);
    }

private:
    static double leader_component(double leader, double current, double a,
                                   detail::random_source& random) {
        const double coefficient_a = 2.0 * a * random.uniform() - a;
        const double coefficient_c = 2.0 * random.uniform();
        return leader - coefficient_a * std::abs(coefficient_c * leader - current);
    }
};

class woa_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(problem, population_size, random, tracker);

        while (problem.remaining() > 0) {
            const detail::position leader = tracker.best_position();
            const double progress = detail::progress(problem);
            const double a = 2.0 * (1.0 - progress);
            const double a2 = -1.0 - progress;
            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate = state.positions[index];
                const double r = random.uniform();
                const double coefficient_a = 2.0 * a * r - a;
                const double coefficient_c = 2.0 * random.uniform();
                const double branch = random.uniform();
                const double spiral_l = (a2 - 1.0) * random.uniform() + 1.0;
                if (branch < 0.5) {
                    const detail::position& target =
                        std::abs(coefficient_a) < 1.0
                            ? leader
                            : state.positions[random.index(state.positions.size())];
                    for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                        const double distance = std::abs(
                            coefficient_c * target[dimension] - state.positions[index][dimension]);
                        candidate[dimension] = target[dimension] - coefficient_a * distance;
                    }
                } else {
                    for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                        const double distance = std::abs(
                            leader[dimension] - state.positions[index][dimension]);
                        candidate[dimension] = distance * std::exp(spiral_l) *
                                                   std::cos(2.0 * pi * spiral_l) +
                                               leader[dimension];
                    }
                }
                detail::clamp(candidate, problem);
                state.fitness[index] = tracker.evaluate(problem, candidate);
                state.positions[index] = std::move(candidate);
            }
        }
        return tracker.result(problem);
    }
};

class sa_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        detail::position current = detail::random_position(problem, random);
        double current_value = tracker.evaluate(problem, current);
        const double range = problem.upper_bound() - problem.lower_bound();

        while (problem.remaining() > 0) {
            const double progress = detail::progress(problem);
            const double temperature = 1000.0 * std::pow(1e-11, progress);
            const double sigma = range * (0.001 + 0.10 * (1.0 - progress));
            detail::position candidate = current;
            for (double& coordinate : candidate) coordinate += sigma * random.normal();
            detail::clamp(candidate, problem);
            const double value = tracker.evaluate(problem, candidate);
            const double difference = value - current_value;
            const bool accept = difference <= 0.0 ||
                                random.uniform() < std::exp(-difference /
                                    std::max(temperature, 1e-15));
            if (accept) {
                current = std::move(candidate);
                current_value = value;
            }
        }
        return tracker.result(problem);
    }
};

class aoa_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(problem, population_size, random, tracker);
        constexpr double alpha = 5.0;
        constexpr double mu = 0.499;
        const double arithmetic_span =
            (problem.upper_bound() - problem.lower_bound()) * mu + problem.lower_bound();

        while (problem.remaining() > 0) {
            const double progress = detail::progress(problem);
            const double mop = 1.0 - std::pow(progress, 1.0 / alpha);
            const double moa = 0.2 + 0.8 * progress;
            const detail::position best = tracker.best_position();
            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate = state.positions[index];
                for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                    if (random.uniform() < moa) {
                        if (random.uniform() > 0.5) {
                            candidate[dimension] = best[dimension] /
                                (mop + std::numeric_limits<double>::epsilon()) * arithmetic_span;
                        } else {
                            candidate[dimension] = best[dimension] * mop * arithmetic_span;
                        }
                    } else if (random.uniform() > 0.5) {
                        candidate[dimension] = best[dimension] - mop * arithmetic_span;
                    } else {
                        candidate[dimension] = best[dimension] + mop * arithmetic_span;
                    }
                }
                detail::clamp(candidate, problem);
                const double value = tracker.evaluate(problem, candidate);
                if (value <= state.fitness[index]) {
                    state.positions[index] = std::move(candidate);
                    state.fitness[index] = value;
                }
            }
        }
        return tracker.result(problem);
    }
};

}  // namespace

std::unique_ptr<optimizer> make_pso_optimizer() { return std::make_unique<pso_optimizer>(); }
std::unique_ptr<optimizer> make_gwo_optimizer() { return std::make_unique<gwo_optimizer>(); }
std::unique_ptr<optimizer> make_woa_optimizer() { return std::make_unique<woa_optimizer>(); }
std::unique_ptr<optimizer> make_sa_optimizer() { return std::make_unique<sa_optimizer>(); }
std::unique_ptr<optimizer> make_aoa_optimizer() { return std::make_unique<aoa_optimizer>(); }

}  // namespace lscbo

