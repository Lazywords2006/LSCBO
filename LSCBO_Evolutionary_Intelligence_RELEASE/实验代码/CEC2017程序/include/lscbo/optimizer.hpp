#ifndef LSCBO_OPTIMIZER_HPP
#define LSCBO_OPTIMIZER_HPP

#include <cstddef>
#include <cstdint>
#include <vector>

namespace lscbo {

class objective {
public:
    virtual ~objective() = default;
    virtual double evaluate(const std::vector<double>& point) = 0;
    virtual int dimensions() const noexcept = 0;
    virtual double lower_bound() const noexcept = 0;
    virtual double upper_bound() const noexcept = 0;
    virtual std::size_t evaluations() const noexcept = 0;
    virtual std::size_t budget() const noexcept = 0;
    virtual std::size_t remaining() const noexcept = 0;
    virtual double optimum() const noexcept = 0;
};

struct checkpoint {
    std::size_t evaluations{};
    double best_value{};
};

struct optimization_result {
    double best_value{};
    std::vector<double> best_position;
    std::size_t evaluations{};
    std::vector<checkpoint> convergence;
};

class optimizer {
public:
    virtual ~optimizer() = default;
    virtual optimization_result run(objective& problem, std::uint64_t seed) = 0;
};

}  // namespace lscbo

#endif

