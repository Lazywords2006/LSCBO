#ifndef LSCBO_PROTOCOL_HPP
#define LSCBO_PROTOCOL_HPP

#include <string>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace lscbo {

const std::vector<std::string>& approved_algorithms();
const std::vector<int>& official_functions();
void validate_algorithm_names(const std::vector<std::string>& names);

struct experiment_task {
    std::string algorithm;
    int function{};
    int run{};
    std::uint64_t seed{};
    std::size_t budget{};
    int dimension{30};

    std::string key() const;
};

std::vector<experiment_task> formal_tasks();
std::vector<experiment_task> smoke_tasks(std::size_t budget = 3000, int runs = 1);
std::vector<experiment_task> make_tasks(
    const std::vector<std::string>& algorithms,
    const std::vector<int>& functions,
    int runs,
    std::size_t budget,
    std::uint64_t seed_base);
std::vector<experiment_task> tasks_for_shard(
    const std::vector<experiment_task>& tasks, int shard_index, int shard_count);

}  // namespace lscbo

#endif
