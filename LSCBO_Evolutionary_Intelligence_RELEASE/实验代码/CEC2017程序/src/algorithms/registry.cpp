#include "lscbo/algorithms.hpp"

#include "lscbo/protocol.hpp"

#include <stdexcept>

namespace lscbo {
namespace {

class unimplemented_optimizer final : public optimizer {
public:
    explicit unimplemented_optimizer(std::string name) : name_{std::move(name)} {}

    optimization_result run(objective&, std::uint64_t) override {
        throw std::runtime_error{"algorithm not implemented: " + name_};
    }

private:
    std::string name_;
};

}  // namespace

std::unique_ptr<optimizer> make_optimizer(
    const std::string& name,
    const algorithm_configuration& configuration) {
    validate_algorithm_names({name});
    if (configuration.lscbo_levy_fraction < 0.0 ||
        configuration.lscbo_levy_fraction > 1.0) {
        throw std::invalid_argument{"LSCBO Levy fraction must be within [0,1]"};
    }
    if (configuration.lscbo_levy_scale <= 0.0 ||
        configuration.lscbo_levy_beta <= 1.0 ||
        configuration.lscbo_levy_beta > 2.0) {
        throw std::invalid_argument{"invalid LSCBO Levy configuration"};
    }
    if (name == "CBO") return make_cbo_optimizer();
    if (name == "LSCBO") return make_lscbo_optimizer(configuration);
    if (name == "PSO") return make_pso_optimizer();
    if (name == "GWO") return make_gwo_optimizer();
    if (name == "WOA") return make_woa_optimizer();
    if (name == "SA") return make_sa_optimizer();
    if (name == "AOA") return make_aoa_optimizer();
    if (name == "HLBO") return make_hlbo_optimizer();
    if (name == "GWCA") return make_gwca_optimizer();
    return std::make_unique<unimplemented_optimizer>(name);
}

}  // namespace lscbo
