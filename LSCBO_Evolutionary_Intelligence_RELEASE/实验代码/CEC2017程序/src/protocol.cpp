#include "lscbo/protocol.hpp"

#include <algorithm>
#include <stdexcept>
#include <sstream>
#include <unordered_set>

namespace lscbo {

const std::vector<std::string>& approved_algorithms() {
    static const std::vector<std::string> names{
        "LSCBO", "CBO", "PSO", "GWO", "WOA",
        "SA", "AOA", "HLBO", "GWCA"};
    return names;
}

const std::vector<int>& official_functions() {
    static const std::vector<int> functions = [] {
        std::vector<int> values{1};
        for (int function = 3; function <= 30; ++function) {
            values.push_back(function);
        }
        return values;
    }();
    return functions;
}

void validate_algorithm_names(const std::vector<std::string>& names) {
    if (names.empty()) {
        throw std::invalid_argument{"algorithm list cannot be empty"};
    }

    const std::unordered_set<std::string> approved{
        approved_algorithms().begin(), approved_algorithms().end()};
    std::unordered_set<std::string> seen;
    for (const auto& name : names) {
        if (approved.find(name) == approved.end()) {
            throw std::invalid_argument{"unknown or forbidden algorithm: " + name};
        }
        if (!seen.insert(name).second) {
            throw std::invalid_argument{"duplicate algorithm: " + name};
        }
    }
}

std::string experiment_task::key() const {
    std::ostringstream output;
    output << "cec2017-d" << dimension << '-' << algorithm << "-f" << function
           << "-r" << run << "-s" << seed << "-b" << budget;
    return output.str();
}

std::vector<experiment_task> make_tasks(
    const std::vector<std::string>& algorithms,
    const std::vector<int>& functions,
    int runs,
    std::size_t budget,
    std::uint64_t seed_base) {
    validate_algorithm_names(algorithms);
    if (functions.empty()) throw std::invalid_argument{"function list cannot be empty"};
    if (runs <= 0) throw std::invalid_argument{"run count must be positive"};
    if (budget == 0) throw std::invalid_argument{"budget must be positive"};
    for (const int function : functions) {
        if (std::find(official_functions().begin(), official_functions().end(), function) ==
            official_functions().end()) {
            throw std::invalid_argument{"unsupported function in protocol: " +
                                        std::to_string(function)};
        }
    }

    std::vector<experiment_task> tasks;
    tasks.reserve(algorithms.size() * functions.size() * static_cast<std::size_t>(runs));
    for (const auto& algorithm : algorithms) {
        for (const int function : functions) {
            for (int run = 1; run <= runs; ++run) {
                const std::uint64_t paired_seed = seed_base +
                    static_cast<std::uint64_t>(function) * 1000ULL +
                    static_cast<std::uint64_t>(run);
                tasks.push_back({algorithm, function, run, paired_seed, budget, 30});
            }
        }
    }
    return tasks;
}

std::vector<experiment_task> formal_tasks() {
    return make_tasks(approved_algorithms(), official_functions(), 30, 300000,
                      202606180000ULL);
}

std::vector<experiment_task> smoke_tasks(std::size_t budget, int runs) {
    return make_tasks(approved_algorithms(), {1, 3, 10, 20, 30}, runs, budget,
                      202606189000ULL);
}

std::vector<experiment_task> tasks_for_shard(
    const std::vector<experiment_task>& tasks, int shard_index, int shard_count) {
    if (shard_count <= 0) throw std::invalid_argument{"shard count must be positive"};
    if (shard_index < 0 || shard_index >= shard_count) {
        throw std::invalid_argument{"shard index is outside the shard count"};
    }
    std::vector<experiment_task> selected;
    for (std::size_t index = 0; index < tasks.size(); ++index) {
        if (static_cast<int>(index % static_cast<std::size_t>(shard_count)) ==
            shard_index) {
            selected.push_back(tasks[index]);
        }
    }
    return selected;
}

}  // namespace lscbo
