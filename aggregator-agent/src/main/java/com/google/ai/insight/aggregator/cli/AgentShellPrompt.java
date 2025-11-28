package com.google.ai.insight.aggregator.cli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

/**
 * Custom prompt for the Agent CLI.
 */
@Component
public class AgentShellPrompt implements PromptProvider {

    @Override
    public AttributedString getPrompt() {
        return new AttributedString(
                "agent-cli> ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)
        );
    }
}