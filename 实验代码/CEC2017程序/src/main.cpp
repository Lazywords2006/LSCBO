#include "lscbo/protocol.hpp"
#include "lscbo/runner.hpp"

#include <filesystem>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

struct options {
    std::string command;
    std::string profile{"formal"};
    std::filesystem::path output{"formal_results"};
    int shard_index{0};
    int shard_count{1};
    int runs{-1};
    std::size_t budget{0};
    std::vector<std::string> algorithms;
    std::vector<int> functions;
    lscbo::algorithm_configuration algorithm_configuration;
};

std::vector<std::string> split(const std::string& text) {
    std::vector<std::string> values;
    std::istringstream input{text};
    std::string value;
    while (std::getline(input, value, ',')) {
        if (!value.empty()) values.push_back(value);
    }
    return values;
}

options parse(int argc, char** argv) {
    if (argc < 2) throw std::invalid_argument{"missing command: run or merge"};
    options result;
    result.command = argv[1];
    for (int index = 2; index < argc; ++index) {
        const std::string argument = argv[index];
        if (index + 1 >= argc) throw std::invalid_argument{"missing value for " + argument};
        const std::string value = argv[++index];
        if (argument == "--profile") result.profile = value;
        else if (argument == "--out") result.output = value;
        else if (argument == "--shard-index") result.shard_index = std::stoi(value);
        else if (argument == "--shard-count") result.shard_count = std::stoi(value);
        else if (argument == "--runs") result.runs = std::stoi(value);
        else if (argument == "--budget") result.budget = std::stoull(value);
        else if (argument == "--algorithms") result.algorithms = split(value);
        else if (argument == "--functions") {
            for (const auto& item : split(value)) result.functions.push_back(std::stoi(item));
        } else if (argument == "--levy-fraction") {
            result.algorithm_configuration.lscbo_levy_fraction = std::stod(value);
        } else if (argument == "--levy-scale") {
            result.algorithm_configuration.lscbo_levy_scale = std::stod(value);
        } else {
            throw std::invalid_argument{"unknown option: " + argument};
        }
    }
    return result;
}

std::vector<lscbo::experiment_task> tasks_for(const options& value) {
    if (value.algorithms.empty() && value.functions.empty() && value.runs < 0 &&
        value.budget == 0) {
        if (value.profile == "formal") return lscbo::formal_tasks();
        if (value.profile == "smoke") return lscbo::smoke_tasks();
        throw std::invalid_argument{"profile must be formal or smoke"};
    }
    const auto algorithms = value.algorithms.empty()
        ? lscbo::approved_algorithms() : value.algorithms;
    const auto functions = value.functions.empty()
        ? lscbo::official_functions() : value.functions;
    const int runs = value.runs < 0 ? (value.profile == "formal" ? 30 : 1) : value.runs;
    const std::size_t budget = value.budget == 0
        ? (value.profile == "formal" ? 300000 : 3000) : value.budget;
    return lscbo::make_tasks(algorithms, functions, runs, budget, 202606180000ULL);
}

void print_usage() {
    std::cout
        << "LSCBO official CEC2017 runner\n"
        << "  run --profile formal|smoke --out DIR --shard-index I --shard-count N\n"
        << "  merge --profile formal|smoke --out DIR --shard-count N\n"
        << "Optional filters: --algorithms A,B --functions 1,3 --runs N --budget N\n";
}

}  // namespace

int main(int argc, char** argv) {
    try {
        if (argc == 1 || std::string{argv[1]} == "help") {
            print_usage();
            return 0;
        }
        const options value = parse(argc, argv);
        const auto tasks = tasks_for(value);
        if (value.command == "run") {
            lscbo::run_task_shard(tasks, value.output, value.shard_index,
                                  value.shard_count, value.algorithm_configuration);
            return 0;
        }
        if (value.command == "merge") {
            const auto report = lscbo::merge_and_analyze(
                tasks, value.output, value.shard_count);
            std::cout << "merged_rows=" << report.rows << '\n';
            return 0;
        }
        throw std::invalid_argument{"unknown command: " + value.command};
    } catch (const std::exception& error) {
        std::cerr << "ERROR: " << error.what() << '\n';
        print_usage();
        return 2;
    }
}
