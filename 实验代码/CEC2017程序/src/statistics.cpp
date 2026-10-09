#include "lscbo/statistics.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <numeric>
#include <stdexcept>
#include <utility>

namespace lscbo {
namespace {

double beta_continued_fraction(double a, double b, double x) {
    constexpr int maximum_iterations = 200;
    constexpr double epsilon = 3e-14;
    constexpr double minimum = std::numeric_limits<double>::min() / epsilon;
    const double qab = a + b;
    const double qap = a + 1.0;
    const double qam = a - 1.0;
    double c = 1.0;
    double d = 1.0 - qab * x / qap;
    if (std::abs(d) < minimum) d = minimum;
    d = 1.0 / d;
    double result = d;
    for (int iteration = 1; iteration <= maximum_iterations; ++iteration) {
        const int twice = 2 * iteration;
        double aa = iteration * (b - iteration) * x /
                    ((qam + twice) * (a + twice));
        d = 1.0 + aa * d;
        if (std::abs(d) < minimum) d = minimum;
        c = 1.0 + aa / c;
        if (std::abs(c) < minimum) c = minimum;
        d = 1.0 / d;
        result *= d * c;
        aa = -(a + iteration) * (qab + iteration) * x /
             ((a + twice) * (qap + twice));
        d = 1.0 + aa * d;
        if (std::abs(d) < minimum) d = minimum;
        c = 1.0 + aa / c;
        if (std::abs(c) < minimum) c = minimum;
        d = 1.0 / d;
        const double delta = d * c;
        result *= delta;
        if (std::abs(delta - 1.0) < epsilon) return result;
    }
    throw std::runtime_error{"incomplete beta did not converge"};
}

double regularized_incomplete_beta(double x, double a, double b) {
    if (x < 0.0 || x > 1.0 || a <= 0.0 || b <= 0.0) {
        throw std::invalid_argument{"invalid incomplete beta arguments"};
    }
    if (x == 0.0 || x == 1.0) return x;
    const double front = std::exp(std::lgamma(a + b) - std::lgamma(a) -
                                  std::lgamma(b) + a * std::log(x) +
                                  b * std::log1p(-x));
    if (x < (a + 1.0) / (a + b + 2.0)) {
        return front * beta_continued_fraction(a, b, x) / a;
    }
    return 1.0 - front * beta_continued_fraction(b, a, 1.0 - x) / b;
}

}  // namespace

std::vector<double> rank_values(const std::vector<double>& values) {
    std::vector<std::size_t> order(values.size());
    std::iota(order.begin(), order.end(), 0);
    std::stable_sort(order.begin(), order.end(), [&](std::size_t left, std::size_t right) {
        return values[left] < values[right];
    });
    std::vector<double> ranks(values.size());
    std::size_t begin = 0;
    while (begin < order.size()) {
        std::size_t end = begin + 1;
        while (end < order.size() && values[order[end]] == values[order[begin]]) ++end;
        const double average = 0.5 *
            (static_cast<double>(begin + 1) + static_cast<double>(end));
        for (std::size_t index = begin; index < end; ++index) {
            ranks[order[index]] = average;
        }
        begin = end;
    }
    return ranks;
}

std::vector<double> holm_adjust(const std::vector<double>& p_values) {
    std::vector<std::size_t> order(p_values.size());
    std::iota(order.begin(), order.end(), 0);
    std::stable_sort(order.begin(), order.end(), [&](std::size_t left, std::size_t right) {
        return p_values[left] < p_values[right];
    });
    std::vector<double> adjusted(p_values.size());
    double previous = 0.0;
    for (std::size_t rank = 0; rank < order.size(); ++rank) {
        const double scaled = p_values[order[rank]] *
            static_cast<double>(order.size() - rank);
        previous = std::max(previous, std::min(1.0, scaled));
        adjusted[order[rank]] = previous;
    }
    return adjusted;
}

descriptive_statistics describe(const std::vector<double>& values) {
    if (values.empty()) throw std::invalid_argument{"cannot describe empty data"};
    const double mean = std::accumulate(values.begin(), values.end(), 0.0) /
                        static_cast<double>(values.size());
    double square_sum = 0.0;
    for (const double value : values) {
        const double difference = value - mean;
        square_sum += difference * difference;
    }
    std::vector<double> ordered = values;
    std::sort(ordered.begin(), ordered.end());
    const std::size_t middle = ordered.size() / 2;
    const double median = ordered.size() % 2 == 0
        ? 0.5 * (ordered[middle - 1] + ordered[middle])
        : ordered[middle];
    const double deviation = values.size() > 1
        ? std::sqrt(square_sum / static_cast<double>(values.size() - 1))
        : 0.0;
    return {mean, deviation, median, ordered.front(), ordered.back()};
}

wilcoxon_result paired_wilcoxon(const std::vector<double>& proposed,
                               const std::vector<double>& comparator) {
    if (proposed.size() != comparator.size() || proposed.empty()) {
        throw std::invalid_argument{"Wilcoxon inputs must be non-empty paired data"};
    }
    struct signed_difference { double absolute; int sign; };
    std::vector<signed_difference> differences;
    std::size_t wins = 0;
    std::size_t losses = 0;
    std::size_t ties = 0;
    for (std::size_t index = 0; index < proposed.size(); ++index) {
        const double difference = proposed[index] - comparator[index];
        if (difference < 0.0) ++wins;
        else if (difference > 0.0) ++losses;
        else { ++ties; continue; }
        differences.push_back({std::abs(difference), difference > 0.0 ? 1 : -1});
    }
    if (differences.empty()) {
        return {proposed.size(), wins, losses, ties, 0.0, 1.0};
    }
    std::stable_sort(differences.begin(), differences.end(),
        [](const auto& left, const auto& right) { return left.absolute < right.absolute; });
    double positive = 0.0;
    double negative = 0.0;
    double tie_correction = 0.0;
    std::size_t begin = 0;
    while (begin < differences.size()) {
        std::size_t end = begin + 1;
        while (end < differences.size() &&
               differences[end].absolute == differences[begin].absolute) ++end;
        const double rank = 0.5 *
            (static_cast<double>(begin + 1) + static_cast<double>(end));
        for (std::size_t index = begin; index < end; ++index) {
            if (differences[index].sign > 0) positive += rank;
            else negative += rank;
        }
        const double count = static_cast<double>(end - begin);
        if (count > 1.0) tie_correction += count * count * count - count;
        begin = end;
    }
    const double count = static_cast<double>(differences.size());
    const double mean = count * (count + 1.0) / 4.0;
    const double variance =
        (count * (count + 1.0) * (2.0 * count + 1.0) - 0.5 * tie_correction) / 24.0;
    const double statistic = std::min(positive, negative);
    const double distance = std::max(0.0, std::abs(positive - mean) - 0.5);
    const double z = variance > 0.0 ? distance / std::sqrt(variance) : 0.0;
    const double p_value = std::erfc(z / std::sqrt(2.0));
    return {proposed.size(), wins, losses, ties, statistic, p_value};
}

double f_distribution_survival(double value, double numerator_degrees,
                               double denominator_degrees) {
    if (value < 0.0 || numerator_degrees <= 0.0 || denominator_degrees <= 0.0) {
        throw std::invalid_argument{"invalid F distribution arguments"};
    }
    if (value == 0.0) return 1.0;
    const double x = denominator_degrees /
        (denominator_degrees + numerator_degrees * value);
    return regularized_incomplete_beta(
        x, denominator_degrees / 2.0, numerator_degrees / 2.0);
}

}  // namespace lscbo

