#ifndef LSCBO_STATISTICS_HPP
#define LSCBO_STATISTICS_HPP

#include <cstddef>
#include <vector>

namespace lscbo {

struct descriptive_statistics {
    double mean{};
    double standard_deviation{};
    double median{};
    double best{};
    double worst{};
};

struct wilcoxon_result {
    std::size_t pairs{};
    std::size_t wins{};
    std::size_t losses{};
    std::size_t ties{};
    double statistic{};
    double p_value{};
};

std::vector<double> rank_values(const std::vector<double>& values);
std::vector<double> holm_adjust(const std::vector<double>& p_values);
descriptive_statistics describe(const std::vector<double>& values);
wilcoxon_result paired_wilcoxon(const std::vector<double>& proposed,
                               const std::vector<double>& comparator);
double f_distribution_survival(double value, double numerator_degrees,
                               double denominator_degrees);

}  // namespace lscbo

#endif

