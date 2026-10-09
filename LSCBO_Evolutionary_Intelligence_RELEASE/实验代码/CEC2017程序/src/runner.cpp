#include "lscbo/runner.hpp"

#include "lscbo/algorithms.hpp"
#include "lscbo/cec_problem.hpp"
#include "lscbo/statistics.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <fstream>
#include <iomanip>
#include <map>
#include <numeric>
#include <set>
#include <sstream>
#include <stdexcept>

namespace lscbo {
namespace {

std::string shard_name(int shard_index) {
    std::ostringstream output;
    output << "shard_" << std::setw(2) << std::setfill('0') << shard_index;
    return output.str();
}

std::size_t comma_count(const std::string& line) {
    return static_cast<std::size_t>(std::count(line.begin(), line.end(), ','));
}

void ensure_header(const std::filesystem::path& path, const std::string& header) {
    if (std::filesystem::exists(path) && std::filesystem::file_size(path) > 0) return;
    std::ofstream output{path, std::ios::out | std::ios::trunc};
    if (!output) throw std::runtime_error{"cannot create output file: " + path.string()};
    output << header << '\n';
}

void write_manifest(const std::filesystem::path& path, int shard_index,
                    int shard_count, std::size_t selected_tasks,
                    const algorithm_configuration& configuration) {
    std::ofstream output{path, std::ios::out | std::ios::trunc};
    if (!output) throw std::runtime_error{"cannot write manifest: " + path.string()};
    output << "{\n"
           << "  \"protocol\": \"official-cec2017-d30-v1\",\n"
           << "  \"functions\": \"F1,F3-F30\",\n"
           << "  \"dimension\": 30,\n"
           << "  \"population\": 30,\n"
           << "  \"shard_index\": " << shard_index << ",\n"
           << "  \"shard_count\": " << shard_count << ",\n"
           << "  \"selected_tasks\": " << selected_tasks << ",\n"
           << "  \"algorithm_order\": "
           << "[\"LSCBO\",\"CBO\",\"PSO\",\"GWO\",\"WOA\","
              "\"SA\",\"AOA\",\"HLBO\",\"GWCA\"],\n"
           << "  \"excluded\": [\"HHO\",\"GTO\",\"DE\",\"CBO_Levy\"],\n"
           << "  \"cbo_variant\": \"manuscript dynamic CBO baseline\",\n"
           << "  \"lscbo_variant\": \"dynamic CBO plus additive Levy search trials\",\n"
           << "  \"lscbo_levy_beta\": " << configuration.lscbo_levy_beta << ",\n"
           << "  \"lscbo_levy_fraction\": " << configuration.lscbo_levy_fraction << ",\n"
           << "  \"lscbo_levy_scale\": " << configuration.lscbo_levy_scale << ",\n"
           << "  \"aoa_variant\": \"authors MATLAB reference implementation\"\n"
           << "}\n";
}

std::vector<std::string> split_csv(const std::string& line) {
    std::vector<std::string> fields;
    std::istringstream input{line};
    std::string field;
    while (std::getline(input, field, ',')) fields.push_back(field);
    return fields;
}

struct result_record {
    std::string task_key;
    std::string algorithm;
    int function{};
    int run{};
    std::uint64_t seed{};
    int dimension{};
    std::size_t budget{};
    std::size_t evaluations{};
    double best_value{};
    double optimum{};
    double error{};
    double elapsed{};
    std::string original_line;
};

result_record parse_result(const std::string& line) {
    const auto fields = split_csv(line);
    if (fields.size() != 12) throw std::runtime_error{"malformed result row"};
    result_record record;
    record.task_key = fields[0];
    record.algorithm = fields[1];
    record.function = std::stoi(fields[2]);
    record.run = std::stoi(fields[3]);
    record.seed = std::stoull(fields[4]);
    record.dimension = std::stoi(fields[5]);
    record.budget = std::stoull(fields[6]);
    record.evaluations = std::stoull(fields[7]);
    record.best_value = std::stod(fields[8]);
    record.optimum = std::stod(fields[9]);
    record.error = std::stod(fields[10]);
    record.elapsed = std::stod(fields[11]);
    record.original_line = line;
    return record;
}

std::string block_key(int function, int run) {
    return std::to_string(function) + "-" + std::to_string(run);
}

}  // namespace

std::set<std::string> read_completed_task_keys(const std::filesystem::path& csv_file) {
    std::set<std::string> keys;
    std::ifstream input{csv_file};
    if (!input) return keys;
    std::string line;
    std::getline(input, line);
    while (std::getline(input, line)) {
        if (line.empty() || comma_count(line) != 11) continue;
        const auto comma = line.find(',');
        if (comma == std::string::npos || comma == 0) continue;
        keys.insert(line.substr(0, comma));
    }
    return keys;
}

void run_task_shard(const std::vector<experiment_task>& tasks,
                    const std::filesystem::path& output_directory,
                    int shard_index,
                    int shard_count,
                    const algorithm_configuration& configuration) {
    const auto selected = tasks_for_shard(tasks, shard_index, shard_count);
    const auto shard_directory = output_directory / shard_name(shard_index);
    std::filesystem::create_directories(shard_directory);
    const auto result_path = shard_directory / "per_run_results.csv";
    const auto convergence_path = shard_directory / "convergence_checkpoints.csv";
    const auto progress_path = shard_directory / "progress.log";
    ensure_header(result_path,
        "task_key,algorithm,function,run,seed,dimension,budget,evaluations,"
        "best_value,optimum,error,elapsed_seconds");
    ensure_header(convergence_path,
        "task_key,evaluations,best_value,error");
    write_manifest(shard_directory / "manifest.json", shard_index, shard_count,
                   selected.size(), configuration);

    auto completed = read_completed_task_keys(result_path);
    std::ofstream results{result_path, std::ios::out | std::ios::app};
    std::ofstream convergence{convergence_path, std::ios::out | std::ios::app};
    std::ofstream progress{progress_path, std::ios::out | std::ios::app};
    if (!results || !convergence || !progress) {
        throw std::runtime_error{"cannot append shard output files"};
    }
    results << std::setprecision(17);
    convergence << std::setprecision(17);

    std::size_t finished = completed.size();
    for (const auto& task : selected) {
        if (completed.find(task.key()) != completed.end()) continue;
        const auto started = std::chrono::steady_clock::now();
        cec_problem problem{task.function, task.budget};
        const auto result = make_optimizer(task.algorithm, configuration)->run(problem, task.seed);
        if (result.evaluations != task.budget || !std::isfinite(result.best_value)) {
            throw std::runtime_error{"invalid optimizer result for task " + task.key()};
        }
        const double optimum = problem.optimum();
        const double error = std::max(0.0, result.best_value - optimum);
        const double elapsed = std::chrono::duration<double>(
            std::chrono::steady_clock::now() - started).count();
        results << task.key() << ',' << task.algorithm << ',' << task.function << ','
                << task.run << ',' << task.seed << ',' << task.dimension << ','
                << task.budget << ',' << result.evaluations << ',' << result.best_value
                << ',' << optimum << ',' << error << ',' << elapsed << '\n';
        results.flush();
        for (const auto& point : result.convergence) {
            convergence << task.key() << ',' << point.evaluations << ','
                        << point.best_value << ','
                        << std::max(0.0, point.best_value - optimum) << '\n';
        }
        convergence.flush();
        completed.insert(task.key());
        ++finished;
        progress << "completed=" << finished << '/' << selected.size()
                 << " task=" << task.key() << " seconds=" << std::fixed
                 << std::setprecision(3) << elapsed << '\n';
        progress.flush();
    }
}

merge_report merge_and_analyze(const std::vector<experiment_task>& tasks,
                               const std::filesystem::path& output_directory,
                               int shard_count) {
    if (shard_count <= 0) throw std::invalid_argument{"shard count must be positive"};
    std::map<std::string, experiment_task> expected;
    for (const auto& task : tasks) {
        if (!expected.emplace(task.key(), task).second) {
            throw std::runtime_error{"protocol contains duplicate task key: " + task.key()};
        }
    }

    std::map<std::string, result_record> records;
    std::size_t duplicates = 0;
    for (int shard = 0; shard < shard_count; ++shard) {
        const auto path = output_directory / shard_name(shard) / "per_run_results.csv";
        std::ifstream input{path};
        if (!input) throw std::runtime_error{"missing shard result file: " + path.string()};
        std::string line;
        std::getline(input, line);
        while (std::getline(input, line)) {
            if (line.empty()) continue;
            const result_record record = parse_result(line);
            const auto task = expected.find(record.task_key);
            if (task == expected.end()) {
                throw std::runtime_error{"unexpected task row: " + record.task_key};
            }
            if (record.algorithm != task->second.algorithm ||
                record.function != task->second.function ||
                record.run != task->second.run ||
                record.seed != task->second.seed ||
                record.dimension != task->second.dimension ||
                record.budget != task->second.budget ||
                record.evaluations != task->second.budget ||
                !std::isfinite(record.best_value) || !std::isfinite(record.error) ||
                record.error < 0.0) {
                throw std::runtime_error{"invalid result fields for task: " + record.task_key};
            }
            if (!records.emplace(record.task_key, record).second) ++duplicates;
        }
    }
    if (duplicates != 0) {
        throw std::runtime_error{"duplicate task rows found during merge"};
    }
    const std::size_t missing = expected.size() - records.size();
    if (missing != 0) {
        throw std::runtime_error{"incomplete shard results: " + std::to_string(missing) +
                                 " task(s) missing"};
    }

    std::filesystem::create_directories(output_directory);
    std::ofstream combined{output_directory / "per_run_results.csv",
                           std::ios::out | std::ios::trunc};
    combined << "task_key,algorithm,function,run,seed,dimension,budget,evaluations,"
                "best_value,optimum,error,elapsed_seconds\n";
    for (const auto& task : tasks) combined << records.at(task.key()).original_line << '\n';

    std::ofstream convergence{output_directory / "convergence_checkpoints.csv",
                              std::ios::out | std::ios::trunc};
    convergence << "task_key,evaluations,best_value,error\n";
    for (int shard = 0; shard < shard_count; ++shard) {
        std::ifstream input{output_directory / shard_name(shard) /
                            "convergence_checkpoints.csv"};
        std::string line;
        std::getline(input, line);
        while (std::getline(input, line)) if (!line.empty()) convergence << line << '\n';
    }

    std::vector<std::string> algorithms;
    std::vector<int> functions;
    for (const auto& task : tasks) {
        if (std::find(algorithms.begin(), algorithms.end(), task.algorithm) == algorithms.end())
            algorithms.push_back(task.algorithm);
        if (std::find(functions.begin(), functions.end(), task.function) == functions.end())
            functions.push_back(task.function);
    }
    std::map<std::pair<int, std::string>, descriptive_statistics> summaries;
    std::map<std::pair<int, std::string>, double> function_ranks;
    std::vector<double> average_ranks(algorithms.size(), 0.0);
    for (const int function : functions) {
        std::vector<double> means;
        for (const auto& algorithm : algorithms) {
            std::vector<double> errors;
            for (const auto& task : tasks) {
                if (task.function == function && task.algorithm == algorithm)
                    errors.push_back(records.at(task.key()).error);
            }
            const auto summary = describe(errors);
            summaries[{function, algorithm}] = summary;
            means.push_back(summary.mean);
        }
        const auto ranks = rank_values(means);
        for (std::size_t index = 0; index < algorithms.size(); ++index) {
            function_ranks[{function, algorithms[index]}] = ranks[index];
            average_ranks[index] += ranks[index] / static_cast<double>(functions.size());
        }
    }

    std::ofstream function_output{output_directory / "per_function_statistics.csv",
                                  std::ios::out | std::ios::trunc};
    function_output << std::setprecision(17)
                    << "function,algorithm,mean,stddev,median,best,worst,rank\n";
    for (const int function : functions) {
        for (const auto& algorithm : algorithms) {
            const auto summary = summaries.at({function, algorithm});
            function_output << function << ',' << algorithm << ',' << summary.mean << ','
                            << summary.standard_deviation << ',' << summary.median << ','
                            << summary.best << ',' << summary.worst << ','
                            << function_ranks.at({function, algorithm}) << '\n';
        }
    }

    const double blocks = static_cast<double>(functions.size());
    const double methods = static_cast<double>(algorithms.size());
    double rank_square_sum = 0.0;
    for (const double rank : average_ranks) rank_square_sum += rank * rank;
    const double friedman = methods > 1.0
        ? 12.0 * blocks / (methods * (methods + 1.0)) * rank_square_sum -
          3.0 * blocks * (methods + 1.0)
        : 0.0;
    const double iman_denominator = blocks * (methods - 1.0) - friedman;
    const double iman = methods > 1.0 && iman_denominator > 0.0
        ? (blocks - 1.0) * friedman / iman_denominator
        : 0.0;
    const double iman_p = methods > 1.0
        ? f_distribution_survival(iman, methods - 1.0,
                                  (methods - 1.0) * (blocks - 1.0))
        : 1.0;
    std::vector<std::size_t> rank_order(algorithms.size());
    std::iota(rank_order.begin(), rank_order.end(), 0);
    std::stable_sort(rank_order.begin(), rank_order.end(), [&](std::size_t left,
                                                               std::size_t right) {
        return average_ranks[left] < average_ranks[right];
    });
    std::ofstream ranking{output_directory / "friedman_ranking.csv",
                          std::ios::out | std::ios::trunc};
    ranking << std::setprecision(17)
            << "position,algorithm,average_rank,friedman_chi_square,"
               "iman_davenport_f,iman_davenport_p\n";
    for (std::size_t position = 0; position < rank_order.size(); ++position) {
        const std::size_t index = rank_order[position];
        ranking << position + 1 << ',' << algorithms[index] << ',' << average_ranks[index]
                << ',' << friedman << ',' << iman << ',' << iman_p << '\n';
    }

    struct pairwise_row { std::string opponent; wilcoxon_result result; };
    std::vector<pairwise_row> pairwise;
    if (std::find(algorithms.begin(), algorithms.end(), "LSCBO") != algorithms.end()) {
        for (const auto& opponent : algorithms) {
            if (opponent == "LSCBO") continue;
            std::vector<double> left;
            std::vector<double> right;
            for (const int function : functions) {
                left.push_back(summaries.at({function, "LSCBO"}).mean);
                right.push_back(summaries.at({function, opponent}).mean);
            }
            pairwise.push_back({opponent, paired_wilcoxon(left, right)});
        }
    }
    std::vector<double> raw_p;
    for (const auto& row : pairwise) raw_p.push_back(row.result.p_value);
    const auto adjusted = holm_adjust(raw_p);
    std::ofstream wilcoxon{output_directory / "pairwise_wilcoxon_holm.csv",
                           std::ios::out | std::ios::trunc};
    wilcoxon << std::setprecision(17)
             << "proposed,opponent,function_pairs,wins,losses,ties,statistic,p_raw,p_holm\n";
    for (std::size_t index = 0; index < pairwise.size(); ++index) {
        const auto& row = pairwise[index];
        wilcoxon << "LSCBO," << row.opponent << ',' << row.result.pairs << ','
                 << row.result.wins << ',' << row.result.losses << ','
                 << row.result.ties << ',' << row.result.statistic << ','
                 << row.result.p_value << ',' << adjusted[index] << '\n';
    }

    std::ofstream manifest{output_directory / "manifest.json",
                           std::ios::out | std::ios::trunc};
    manifest << "{\n  \"protocol\": \"official-cec2017-d30-v1\",\n"
             << "  \"complete\": true,\n"
             << "  \"rows\": " << records.size() << ",\n"
             << "  \"shards\": " << shard_count << "\n}\n";
    return {records.size(), duplicates, missing};
}

}  // namespace lscbo
