// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
#include "PromptHandler.hpp"
#include "GenieWrapper.hpp"

using namespace AppUtils;

// Llama3 prompt
constexpr const std::string_view c_bot_name = "QBot";
constexpr const std::string_view c_first_prompt_prefix_part_1 =
        "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\nYour name is ";
constexpr const std::string_view c_first_prompt_prefix_part_2 =
        "and you are a helpful AI assistant. Follow these instructions exactly:\n"
        "- If the user asks where to go or how to get somewhere (e.g., '어디로 가야 해?', '어디야?', '거기 어떻게 가?'), respond with **only** the following format—no extra text, line breaks, or explanations: <Place-name>,<Latitude>,<Longitude>\n"
        "\n"
        "Example  \n"
        "Seoul Station,37.5563,126.9723\n"
        "- For all other questions, respond naturally and informatively in full Korean sentences.\n"
        "<|eot_id|>";
constexpr const std::string_view c_prompt_prefix = "<|start_header_id|>user<|end_header_id|>\n\n";
constexpr const std::string_view c_end_of_prompt = "<|eot_id|>";
constexpr const std::string_view c_assistant_header = "<|start_header_id|>assistant<|end_header_id|>\n\n";

PromptHandler::PromptHandler()
        : m_is_first_prompt(true)
{
}

std::string PromptHandler::GetPromptWithTag(const std::string& user_prompt)
{
    // Ref: https://www.llama.com/docs/model-cards-and-prompt-formats/meta-llama-3/
    if (m_is_first_prompt)
    {
        m_is_first_prompt = false;
        return std::string(c_first_prompt_prefix_part_1) + c_bot_name.data() + c_first_prompt_prefix_part_2.data() +
               c_prompt_prefix.data() + user_prompt + c_end_of_prompt.data() + c_assistant_header.data();
    }
    return std::string(c_prompt_prefix) + user_prompt.data() + c_end_of_prompt.data() + c_assistant_header.data();
}
