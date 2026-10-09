#ifndef LSCBO_CEC_PROBLEM_HPP
#define LSCBO_CEC_PROBLEM_HPP

#include "lscbo/optimizer.hpp"

#include <cstddef>
#include <vector>

namespace lscbo {

class cec_problem final : public objective {
public:
    static constexpr int dimension = 30;

    cec_problem(int function, std::size_t budget);

    double evaluate(const std::vector<double>& point) override;
    int dimensions() const noexcept override;
    double lower_bound() const noexcept override;
    double upper_bound() const noexcept override;
    std::size_t evaluations() const noexcept override;
    std::size_t budget() const noexcept override;
    std::size_t remaining() const noexcept override;
    int function() const noexcept;
    double optimum() const noexcept override;

private:
    int function_;
    std::size_t budget_;
    std::size_t evaluations_{0};
};

}  // namespace lscbo

#endif
