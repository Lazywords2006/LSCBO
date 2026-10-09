#ifndef LSCBO_ALGORITHMS_HPP
#define LSCBO_ALGORITHMS_HPP

#include "lscbo/optimizer.hpp"

#include <memory>
#include <string>

namespace lscbo {

struct algorithm_configuration {
    double lscbo_levy_fraction{0.30};
    double lscbo_levy_scale{0.03};
    double lscbo_levy_beta{1.5};
};

std::unique_ptr<optimizer> make_optimizer(
    const std::string& name,
    const algorithm_configuration& configuration = {});
std::unique_ptr<optimizer> make_cbo_optimizer();
std::unique_ptr<optimizer> make_lscbo_optimizer(
    const algorithm_configuration& configuration);
std::unique_ptr<optimizer> make_pso_optimizer();
std::unique_ptr<optimizer> make_gwo_optimizer();
std::unique_ptr<optimizer> make_woa_optimizer();
std::unique_ptr<optimizer> make_sa_optimizer();
std::unique_ptr<optimizer> make_aoa_optimizer();
std::unique_ptr<optimizer> make_hlbo_optimizer();
std::unique_ptr<optimizer> make_gwca_optimizer();

}  // namespace lscbo

#endif
