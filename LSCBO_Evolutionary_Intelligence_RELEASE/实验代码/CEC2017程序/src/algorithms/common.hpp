#ifndef LSCBO_ALGORITHM_COMMON_HPP
#define LSCBO_ALGORITHM_COMMON_HPP

#include "lscbo/optimizer.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <random>
#include <stdexcept>
#include <utility>
#include <vector>

namespace lscbo::detail {

using position = std::vector<double>;

class random_source {
public:
    explicit random_source(std::uint64_t seed) : engine_{seed} {}

    double uniform() { return uniform_(engine_); }
    double uniform(double lower, double upper) {
        return lower + (upper - lower) * uniform();
    }
    double normal() { return normal_(engine_); }
    std::size_t index(std::size_t count) {
        if (count == 0) throw std::invalid_argument{"cannot sample an empty index range"};
        std::uniform_int_distribution<std::size_t> distribution{0, count - 1};
        return distribution(engine_);
    }
    int integer(int lower, int upper) {
        std::uniform_int_distribution<int> distribution{lower, upper};
        return distribution(engine_);
    }

private:
    std::mt19937_64 engine_;
    std::uniform_real_distribution<double> uniform_{0.0, 1.0};
    std::normal_distribution<double> normal_{0.0, 1.0};
};

inline double clamp(double value, const objective& problem) {
    return std::max(problem.lower_bound(), std::min(problem.upper_bound(), value));
}

inline void clamp(position& point, const objective& problem) {
    for (double& value : point) value = clamp(value, problem);
}

inline position random_position(const objective& problem, random_source& random) {
    position point(static_cast<std::size_t>(problem.dimensions()));
    for (double& value : point) {
        value = random.uniform(problem.lower_bound(), problem.upper_bound());
    }
    return point;
}

class best_tracker {
public:
    explicit best_tracker(const objective& problem)
        : checkpoint_interval_{std::max<std::size_t>(1, problem.budget() / 100)},
          next_checkpoint_{checkpoint_interval_} {}

    void observe(const objective& problem, const position& point, double value) {
        if (value < best_value_) {
            best_value_ = value;
            best_position_ = point;
        }
        while (problem.evaluations() >= next_checkpoint_ &&
               next_checkpoint_ <= problem.budget()) {
            convergence_.push_back({next_checkpoint_, best_value_});
            next_checkpoint_ += checkpoint_interval_;
        }
        if (problem.evaluations() == problem.budget() &&
            (convergence_.empty() || convergence_.back().evaluations != problem.budget())) {
            convergence_.push_back({problem.budget(), best_value_});
        }
    }

    double evaluate(objective& problem, const position& point) {
        const double value = problem.evaluate(point);
        observe(problem, point, value);
        return value;
    }

    const position& best_position() const noexcept { return best_position_; }
    double best_value() const noexcept { return best_value_; }

    optimization_result result(const objective& problem) const {
        return {best_value_, best_position_, problem.evaluations(), convergence_};
    }

private:
    double best_value_{std::numeric_limits<double>::infinity()};
    position best_position_;
    std::size_t checkpoint_interval_;
    std::size_t next_checkpoint_;
    std::vector<checkpoint> convergence_;
};

struct population {
    std::vector<position> positions;
    std::vector<double> fitness;
};

inline population initialize_population(objective& problem, std::size_t requested,
                                        random_source& random, best_tracker& tracker) {
    population state;
    const std::size_t count = std::min(requested, problem.remaining());
    state.positions.reserve(count);
    state.fitness.reserve(count);
    for (std::size_t index = 0; index < count; ++index) {
        position point = random_position(problem, random);
        const double value = tracker.evaluate(problem, point);
        state.positions.push_back(std::move(point));
        state.fitness.push_back(value);
    }
    return state;
}

inline std::size_t best_index(const std::vector<double>& fitness) {
    return static_cast<std::size_t>(
        std::distance(fitness.begin(), std::min_element(fitness.begin(), fitness.end())));
}

inline std::size_t worst_index(const std::vector<double>& fitness) {
    return static_cast<std::size_t>(
        std::distance(fitness.begin(), std::max_element(fitness.begin(), fitness.end())));
}

inline double progress(const objective& problem) {
    return static_cast<double>(problem.evaluations()) /
           static_cast<double>(problem.budget());
}

}  // namespace lscbo::detail

#endif
