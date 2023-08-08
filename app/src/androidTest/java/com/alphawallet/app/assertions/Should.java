package com.alphawallet.app.assertions;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static com.alphawallet.app.util.Helper.waitUntil;

import org.hamcrest.CoreMatchers;

public class Should
{
    private static final int TIMEOUT_IN_SECONDS = 5 * 60;

    public static void shouldSee(String text)
    {
        onView(isRoot()).perform(waitUntil(withSubstring(text), TIMEOUT_IN_SECONDS));
    }

    public static void shouldNotSee(String text)
    {
        onView(isRoot()).perform(waitUntil(CoreMatchers.not(withSubstring(text)), TIMEOUT_IN_SECONDS));
    }

    public static void shouldNotSee(int id)
    {
        onView(isRoot()).perform(waitUntil(CoreMatchers.not(withId(id)), TIMEOUT_IN_SECONDS));
    }

    public static void shouldSee(int id)
    {
        onView(isRoot()).perform(waitUntil(withId(id), 10 * 60));
    }
}
