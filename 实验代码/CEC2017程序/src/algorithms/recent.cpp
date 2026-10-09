#include "lscbo/algorithms.hpp"

#include "common.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <memory>
#include <numeric>
#include <vector>

namespace lscbo {
namespace {

constexpr std::size_t population_size = 30;

std::size_t distinct_index(detail::random_source& random, std::size_t count,
                           std::size_t excluded) {
    std::size_t value = random.index(count);
    while (value == excluded && count > 1) value = random.index(count);
    return value;
}

class hlbo_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = detail::initialize_population(problem, population_size, random, tracker);
        const std::size_t evaluations_per_generation = 3 * state.positions.size();
        const std::size_t generations = std::max<std::size_t>(
            1, (problem.budget() - problem.evaluations() +
                evaluations_per_generation - 1) / evaluations_per_generation);
        std::size_t generation = 0;

        while (problem.remaining() > 0) {
            ++generation;
            const std::size_t best = detail::best_index(state.fitness);
            const std::size_t worst = detail::worst_index(state.fitness);
            std::vector<double> quality(state.positions.size(), 0.0);
            double quality_sum = 0.0;
            for (std::size_t index = 0; index < state.positions.size(); ++index) {
                quality[index] = state.fitness[index] - state.fitness[worst];
                quality_sum += quality[index];
            }
            if (std::abs(quality_sum) > 1e-15) {
                for (double& value : quality) value /= quality_sum;
            } else {
                std::fill(quality.begin(), quality.end(),
                          1.0 / static_cast<double>(quality.size()));
            }

            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                const std::size_t other = distinct_index(random, state.positions.size(), index);
                const double denominator = quality[index] + quality[best] + quality[other];
                const double safe = std::abs(denominator) < 1e-15 ? 1.0 : denominator;
                const double own_coefficient = quality[index] / safe;
                const double best_coefficient = quality[best] / safe;
                const double other_coefficient = quality[other] / safe;
                detail::position leader = state.positions[index];
                for (std::size_t dimension = 0; dimension < leader.size(); ++dimension) {
                    leader[dimension] =
                        own_coefficient * state.positions[index][dimension] +
                        best_coefficient * state.positions[best][dimension] +
                        other_coefficient * state.positions[other][dimension];
                }
                detail::clamp(leader, problem);
                const double leader_value = tracker.evaluate(problem, leader);
                if (problem.remaining() == 0) break;

                detail::position candidate = state.positions[index];
                const int integer = random.integer(1, 2);
                for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
                    if (leader_value < state.fitness[index]) {
                        candidate[dimension] += random.uniform() *
                            (leader[dimension] + integer * state.positions[index][dimension]);
                    } else {
                        candidate[dimension] += random.uniform() *
                            (state.positions[index][dimension] - leader[dimension]);
                    }
                }
                detail::clamp(candidate, problem);
                const double value = tracker.evaluate(problem, candidate);
                if (value < state.fitness[index]) {
                    state.positions[index] = std::move(candidate);
                    state.fitness[index] = value;
                }

                if (problem.remaining() == 0) break;
                detail::position local = state.positions[index];
                const double shrink = 1.0 - std::min(
                    1.0, static_cast<double>(generation) /
                             static_cast<double>(generations));
                for (double& coordinate : local) {
                    coordinate += (1.0 - 2.0 * random.uniform()) * 0.2 * shrink * coordinate;
                }
                detail::clamp(local, problem);
                const double local_value = tracker.evaluate(problem, local);
                if (local_value < state.fitness[index]) {
                    state.positions[index] = std::move(local);
                    state.fitness[index] = local_value;
                }
            }
        }
        return tracker.result(problem);
    }
};

double gamma_pdf(double x, double shape, double scale) {
    if (x <= 0.0) return 0.0;
    const double log_value = (shape - 1.0) * std::log(x) - x / scale -
                             std::lgamma(shape) - shape * std::log(scale);
    return std::exp(log_value);
}

class gwca_optimizer final : public optimizer {
public:
    optimization_result run(objective& problem, std::uint64_t seed) override {
        detail::random_source random{seed};
        detail::best_tracker tracker{problem};
        auto state = logistic_population(problem, random, tracker);
        std::vector<detail::position> personal_best = state.positions;
        std::vector<double> personal_fitness = state.fitness;
        const std::size_t eliminated = static_cast<std::size_t>(
            std::ceil(0.1 * static_cast<double>(state.positions.size())));
        const std::size_t evaluations_per_generation = state.positions.size() + eliminated;
        const std::size_t generations = std::max<std::size_t>(
            1, (problem.budget() - problem.evaluations() +
                evaluations_per_generation - 1) / evaluations_per_generation);
        std::size_t generation = 0;

        while (problem.remaining() > 0) {
            ++generation;
            const auto leaders = leader_indices(state.fitness);
            const double time = static_cast<double>(generation);
            const double normalized = std::min(
                1.0, time / static_cast<double>(generations));
            for (std::size_t index = 0;
                 index < state.positions.size() && problem.remaining() > 0; ++index) {
                detail::position candidate = state.positions[index];
                const int role = random.integer(1, 3);
                if (role == 1) {
                    engineer(candidate, state.positions[index],
                             state.positions[leaders[0]], time, normalized, random);
                } else if (role == 2) {
                    soldier(candidate, index, state, state.positions[leaders[1]],
                            normalized, random);
                } else {
                    laborer(candidate, state.positions[index],
                            state.positions[leaders[2]], personal_best[index],
                            normalized, random);
                }
                detail::clamp(candidate, problem);
                const double value = tracker.evaluate(problem, candidate);
                state.positions[index] = candidate;
                state.fitness[index] = value;
                if (value < personal_fitness[index]) {
                    personal_best[index] = std::move(candidate);
                    personal_fitness[index] = value;
                }
            }

            std::vector<std::size_t> order(state.positions.size());
            std::iota(order.begin(), order.end(), 0);
            std::stable_sort(order.begin(), order.end(), [&](std::size_t left, std::size_t right) {
                return state.fitness[left] > state.fitness[right];
            });
            for (std::size_t rank = 0;
                 rank < std::min(eliminated, order.size()) && problem.remaining() > 0; ++rank) {
                const std::size_t index = order[rank];
                detail::position replacement = detail::random_position(problem, random);
                const double value = tracker.evaluate(problem, replacement);
                state.positions[index] = replacement;
                state.fitness[index] = value;
                personal_best[index] = std::move(replacement);
                personal_fitness[index] = value;
            }
        }
        return tracker.result(problem);
    }

private:
    static detail::population logistic_population(objective& problem,
                                                  detail::random_source& random,
                                                  detail::best_tracker& tracker) {
        detail::population state;
        const std::size_t count = std::min(population_size, problem.remaining());
        const double range = problem.upper_bound() - problem.lower_bound();
        for (std::size_t index = 0; index < count; ++index) {
            detail::position point(problem.dimensions());
            for (double& coordinate : point) {
                double chaotic = random.uniform();
                chaotic = 4.0 * chaotic * (1.0 - chaotic);
                coordinate = problem.lower_bound() + chaotic * range;
            }
            const double value = tracker.evaluate(problem, point);
            state.positions.push_back(std::move(point));
            state.fitness.push_back(value);
        }
        return state;
    }

    static std::vector<std::size_t> leader_indices(const std::vector<double>& fitness) {
        std::vector<std::size_t> order(fitness.size());
        std::iota(order.begin(), order.end(), 0);
        std::stable_sort(order.begin(), order.end(), [&](std::size_t left, std::size_t right) {
            return fitness[left] < fitness[right];
        });
        while (order.size() < 3) order.push_back(order.back());
        order.resize(3);
        return order;
    }

    static void engineer(detail::position& candidate, const detail::position& current,
                         const detail::position& leader, double time, double normalized,
                         detail::random_source& random) {
        constexpr double mass = 3.0;
        constexpr double thrust = 8.3;
        constexpr double gravity = 9.8;
        const double c = std::log((std::exp(3.0) - std::exp(2.0)) *
                                  (1.0 - normalized) + std::exp(2.0));
        const double height = 1.0 - normalized;
        const double tail = gamma_pdf(time, 9.0, 6.0);
        for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
            const double angle = 80.0 * random.uniform();
            const double sine = std::sin(angle);
            const double safe_sine = std::abs(sine) < 1e-8
                ? (sine < 0.0 ? -1e-8 : 1e-8) : sine;
            const double acceleration = thrust * (1.0 - normalized) / mass -
                                        gravity * height / safe_sine;
            const double velocity = acceleration * c * tail;
            const double study = (random.integer(0, 1) == 0 ? -1.0 : 1.0) *
                                 (leader[dimension] - current[dimension]) * random.uniform();
            candidate[dimension] = leader[dimension] + study +
                                   current[dimension] * velocity * random.uniform();
        }
    }

    static void soldier(detail::position& candidate, std::size_t index,
                        const detail::population& state, const detail::position& leader,
                        double normalized, detail::random_source& random) {
        constexpr double mass = 3.0;
        constexpr double gravity = 9.8;
        const double c = std::log((std::exp(3.0) - std::exp(2.0)) *
                                  (1.0 - normalized) + std::exp(2.0));
        std::size_t nearest = index == 0 ? 1 : 0;
        double distance = std::abs(state.fitness[nearest] - state.fitness[index]);
        for (std::size_t other = 0; other < state.positions.size(); ++other) {
            if (other == index) continue;
            const double candidate_distance = std::abs(
                state.fitness[other] - state.fitness[index]);
            if (candidate_distance < distance) {
                distance = candidate_distance;
                nearest = other;
            }
        }
        const double tail = gamma_pdf(std::max(1e-9, normalized), 9.0, 6.0);
        for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
            const double angle = 80.0 * random.uniform();
            const double sine = std::sin(angle);
            const double safe_sine = std::abs(sine) < 1e-8
                ? (sine < 0.0 ? -1e-8 : 1e-8) : sine;
            const double velocity = mass * gravity * (1.0 - normalized) /
                                    safe_sine * c * tail;
            const double sign = state.fitness[nearest] < state.fitness[index] ? -1.0 : 1.0;
            const double improve = sign *
                (state.positions[nearest][dimension] - state.positions[index][dimension]) *
                velocity * random.uniform();
            const double study = (leader[dimension] - state.positions[index][dimension]) *
                                 random.uniform();
            candidate[dimension] += improve + study;
        }
    }

    static void laborer(detail::position& candidate, const detail::position& current,
                        const detail::position& leader,
                        const detail::position& personal_best, double normalized,
                        detail::random_source& random) {
        const double tail = gamma_pdf(std::max(1e-9, normalized), 9.0, 6.0);
        for (std::size_t dimension = 0; dimension < candidate.size(); ++dimension) {
            candidate[dimension] = current[dimension] +
                2.0 * (leader[dimension] - current[dimension]) * random.uniform() +
                (personal_best[dimension] - current[dimension]) * tail;
        }
    }
};

}  // namespace

std::unique_ptr<optimizer> make_hlbo_optimizer() { return std::make_unique<hlbo_optimizer>(); }
std::unique_ptr<optimizer> make_gwca_optimizer() { return std::make_unique<gwca_optimizer>(); }

}  // namespace lscbo
