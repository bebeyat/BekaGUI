package dev.bekagui;

/** What happens when a button is clicked. Runs on the player's thread (main thread, or the region thread on Folia). */
@FunctionalInterface
public interface ClickHandler {
    void onClick(Click click);
}
