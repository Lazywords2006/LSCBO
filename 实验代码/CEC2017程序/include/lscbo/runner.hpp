#ifndef LSCBO_RUNNER_HPP
#define LSCBO_RUNNER_HPP

#include "lscbo/protocol.hpp"
#include "lscbo/algorithms.hpp"

#include <filesystem>
#include <set>
#include <string>
#include <vector>

namespace lscbo {

std::set<std::string> read_completed_task_keys(const std::filesystem::path& csv_file);

void run_task_shard(const std::vector<experiment_task>& tasks,
                    const std::filesystem::path& output_directory,
                    int shard_index,
                    int shard_count,
                    const algorithm_configuration& configuration = {});

struct merge_report {
    std::size_t rows{};
    std::size_t duplicates{};
    std::size_t missing{};
};

merge_report merge_and_analyze(const std::vector<experiment_task>& tasks,
                               const std::filesystem::path& output_directory,
                               int shard_count);

}  // namespace lscbo

#endif
