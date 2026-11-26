#include "common.hpp"
#include "gen_verifier.hpp"
#include "judge_engine.hpp"

#include <string>

static CLIConfig parse_arguments(int argc, char* argv[]) {
    CLIConfig config;
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--mode" && i + 1 < argc) config.mode = argv[++i];
        else if (arg == "--prob-dir" && i + 1 < argc) config.prob_dir = argv[++i];
        else if (arg == "--src" && i + 1 < argc) config.src_path = argv[++i];
        else if (arg == "--gen-src" && i + 1 < argc) config.gen_src = argv[++i];
        else if (arg == "--std-src" && i + 1 < argc) config.std_src = argv[++i];
        else if (arg == "--brute-src" && i + 1 < argc) config.brute_src = argv[++i];
        else if (arg == "--plan" && i + 1 < argc) config.plan_path = argv[++i];
        else if (arg == "--lang" && i + 1 < argc) config.lang = argv[++i];
        else if (arg == "--time" && i + 1 < argc) config.time_limit_ms = std::stoi(argv[++i]);
        else if (arg == "--mem" && i + 1 < argc) config.memory_limit_mb = std::stoi(argv[++i]);
        else if (arg == "--json") config.json_output = true;
    }
    return config;
}

int main(int argc, char* argv[]) {
    CLIConfig config = parse_arguments(argc, argv);

    if (config.mode == "gen-verify") {
        return GenVerifier::execute(config);
    }
    return JudgeEngine::execute(config);
}

