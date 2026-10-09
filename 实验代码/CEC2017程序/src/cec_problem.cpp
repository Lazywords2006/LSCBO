#include "lscbo/cec_problem.hpp"

#include "lscbo/protocol.hpp"

#include <algorithm>
#include <cmath>
#include <filesystem>
#include <stdexcept>
#include <string>

void cec17_test_func(double* x, double* f, int nx, int mx, int func_num);

namespace lscbo {
namespace {

void require_file(const std::filesystem::path& path) {
    if (!std::filesystem::is_regular_file(path)) {
        throw std::runtime_error{"missing official CEC2017 data file: " + path.string()};
    }
}

void validate_data_files(int function) {
    const std::filesystem::path root{"input_data"};
    require_file(root / ("M_" + std::to_string(function) + "_D30.txt"));
    require_file(root / ("shift_data_" + std::to_string(function) + ".txt"));
    if ((function >= 11 && function <= 20) || function == 29 || function == 30) {
        require_file(root / ("shuffle_data_" + std::to_string(function) + "_D30.txt"));
    }
}

}  // namespace

cec_problem::cec_problem(int function, std::size_t budget)
    : function_{function}, budget_{budget} {
    if (std::find(official_functions().begin(), official_functions().end(), function) ==
        official_functions().end()) {
        throw std::invalid_argument{"unsupported CEC2017 function: " +
                                    std::to_string(function)};
    }
    if (budget == 0) {
        throw std::invalid_argument{"evaluation budget must be positive"};
    }
    validate_data_files(function);
}

double cec_problem::evaluate(const std::vector<double>& point) {
    if (point.size() != dimension) {
        throw std::invalid_argument{"CEC2017 point must have dimension 30"};
    }
    if (evaluations_ >= budget_) {
        throw std::runtime_error{"CEC2017 evaluation budget exhausted"};
    }
    if (!std::all_of(point.begin(), point.end(), [](double value) {
            return std::isfinite(value);
        })) {
        throw std::invalid_argument{"CEC2017 point contains a non-finite value"};
    }

    std::vector<double> mutable_point{point};
    double value = 0.0;
    cec17_test_func(mutable_point.data(), &value, dimension, 1, function_);
    ++evaluations_;
    if (!std::isfinite(value)) {
        throw std::runtime_error{"official CEC2017 evaluator returned a non-finite value"};
    }
    return value;
}

std::size_t cec_problem::evaluations() const noexcept { return evaluations_; }
int cec_problem::dimensions() const noexcept { return dimension; }
double cec_problem::lower_bound() const noexcept { return -100.0; }
double cec_problem::upper_bound() const noexcept { return 100.0; }
std::size_t cec_problem::budget() const noexcept { return budget_; }
std::size_t cec_problem::remaining() const noexcept { return budget_ - evaluations_; }
int cec_problem::function() const noexcept { return function_; }
double cec_problem::optimum() const noexcept { return function_ * 100.0; }

}  // namespace lscbo
