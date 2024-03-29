package com.alphawallet.app.steps;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.actionWithAssertions;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.alphawallet.app.assertions.Should.shouldNotSee;
import static com.alphawallet.app.assertions.Should.shouldSee;
import static com.alphawallet.app.util.Helper.click;
import static com.alphawallet.app.util.Helper.clickListItem;
import static com.alphawallet.app.util.Helper.clickStaticListItem;
import static com.alphawallet.app.util.Helper.hasView;
import static com.alphawallet.app.util.Helper.waitForLoadingComplete;
import static com.alphawallet.app.util.Helper.waitUntil;
import static com.alphawallet.app.util.Helper.waitUntilThenBack;
import static com.alphawallet.app.util.RootUtil.isDeviceRooted;
import static org.hamcrest.core.StringStartsWith.startsWith;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;

import com.alphawallet.app.R;
import com.alphawallet.app.assertions.Should;
import com.alphawallet.app.util.GetTextAction;
import com.alphawallet.app.util.Helper;
import com.alphawallet.app.util.ScrollToActionImproved;

import org.hamcrest.CoreMatchers;

/**
 * Every step consists of several operations, step name stands for user perspective actions.
 * You can add steps as you wish to reuse code between test cases.
 */
public class Steps
{
    //public static final String GANACHE_URL = "http://10.0.2.2:8545/";
    public static final String GANACHE_URL = "http://192.168.50.128:8545/";

    public static void createNewWallet()
    {
        click(withId(R.id.button_create));
        closeSelectNetworkPage(false);
        click(withText(R.string.action_close));
    }

    public static void closeSecurityWarning()
    {
        if (isDeviceRooted())
        {
            click(withText(R.string.ok));
        }
    }

    public static void closeSelectNetworkPage(boolean isImportWallet)
    {
        Helper.wait(2);
        //wait for network select screen to appear

        //shouldSee("Enabled Networks (1)");
        pressBack();
        waitUntil(withId(R.id.nav_settings_text), 30);



    }

    public static void visit(String urlString)
    {
        Helper.wait(1);
        navigateToBrowser();
        onView(withId(R.id.url_tv)).perform(replaceText(urlString), pressImeActionButton());
    }

    public static void navigateToBrowser()
    {
        click(withId(R.id.nav_browser_text));
    }

    public static ViewAction scrollToImproved()
    {
        return actionWithAssertions(new ScrollToActionImproved());
    }

    public static void selectTestNet(String name)
    {
        gotoSettingsPage();
        selectMenu("Select Active Networks");
        clickStaticListItem(withSubstring("Ethereum")); //deactivate eth
        onView(withId(R.id.network_scroller)).perform(swipeUp());
        Helper.wait(1);
        onView(CoreMatchers.allOf(withId(R.id.switch_material), isDescendantOfA(withId(R.id.testnet_header)))).perform(ViewActions.click());

        click(withText(R.string.action_enable_testnet));
        Helper.wait(1);

        //onView(withSubstring("Görli")).perform(scrollToImproved());
        //clickStaticListItem(withSubstring("Görli"));
        onView(withSubstring(name)).perform(scrollToImproved());
        clickStaticListItem(withSubstring(name));
        Helper.wait(1);
        pressBack();
    }

    public static void selectMenu(String text)
    {
        ViewInteraction selectActiveNetworks = onView(withText(text));
        selectActiveNetworks.perform(scrollTo(), ViewActions.click());
    }

    public static void assertBalanceIs(String balanceStr)
    {
        Should.shouldSee(balanceStr);
    }

    public static void ensureTransactionConfirmed()
    {
//        onView(withText(R.string.rate_no_thanks)).perform(click());
        click(withId(R.string.action_show_tx_details));
        onView(isRoot()).perform(waitUntil(withSubstring("Send"), 30 * 60));
        pressBack();
    }

    public static void sendBalanceTo(String tokenSymbol, String amountStr, String receiverAddress)
    {
        click(withId(R.id.nav_wallet_text));
        ensureBalanceFetched();
        click(withSubstring(tokenSymbol));
        click(withText("Send"));
        onView(withHint("0")).perform(replaceText(amountStr));
        onView(withHint(R.string.recipient_address)).perform(replaceText(receiverAddress));
        click(withId(R.string.action_next));
        Helper.wait(1);
        try
        {
            click(withId(R.string.action_confirm));
        }
        catch (Error | Exception e)
        {
            waitForLoadingComplete("Calculating Gas Limit");
            click(withId(R.string.action_confirm));
        }
    }

    private static void ensureBalanceFetched()
    {
        shouldSee("Ganache");
        shouldNotSee("0 GETH");
    }

    public static void switchToWallet(String address)
    {
        gotoSettingsPage();
        click(withText("Change / Add Wallet"));
        onView(withSubstring(address.substring(0, 6))).perform(ViewActions.click());
        waitUntil(withSubstring("Buy"), 30);
    }

    public static String getWalletAddress()
    {
        gotoSettingsPage();
        click(withText("Show My Wallet Address"));
        GetTextAction getTextAction = new GetTextAction();
        onView(withText(startsWith("0x"))).perform(getTextAction);
        pressBack();
        return getTextAction.getText().toString().replace(" ", ""); // The address show on 2 lines so there is a blank space
    }

    public static void importWalletFromSettingsPage(String text)
    {
        gotoSettingsPage();
        click(withText("Change / Add Wallet"));
        click(withId(R.id.action_add));
//        SnapshotUtil.take("after-add");
        click(withId(R.id.import_account_action));
        int textId;
        int buttonId;
        boolean isPrivateKey = text.startsWith("0x");
        if (isPrivateKey)
        {
            click(withText("Private key"));
            textId = R.id.input_private_key;
            buttonId = R.id.import_action_pk;
        }
        else
        {
            textId = R.id.input_seed;
            buttonId = R.id.import_action;
        }
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(textId)))))).perform(replaceText(text));
        Helper.wait(2); // Avoid error: Error performing a ViewAction! soft keyboard dismissal animation may have been in the way. Retrying once after: 1000 millis
        click(withId(buttonId));
        Helper.wait(2);
        //waitUntil(withSubstring("Manage Wallet"), 2);
        if (Helper.hasView("Lose this Wallet"))
        {
            pressBack();
            Helper.wait(2);
        }
//        waitForLoadingComplete("Handling");
        closeSelectNetworkPage(true);
    }

    private void addGanache()
    {
        waitUntilThenBack(withSubstring("Manage Wallet"), 10);
        Helper.wait(1);
        ViewActions.pressBack();

        waitUntil(withSubstring("Enabled Networks"), 10);
        Helper.wait(1);
        waitUntil(CoreMatchers.anyOf(withText(R.string.action_add), withId(R.id.action_add)));

        onView(CoreMatchers.anyOf(withText(R.string.action_add), withId(R.id.action_add))).perform(ViewActions.click());
        //click(withId(R.id.action_add));
        input(R.id.input_network_name, "Ganache");
        input(R.id.input_network_rpc_url, GANACHE_URL);
        input(R.id.input_network_chain_id, "2");
        input(R.id.input_network_symbol, "ETH");
        input(R.id.input_network_explorer_api, GANACHE_URL);
        input(R.id.input_network_block_explorer_url, GANACHE_URL);
        onView(withId(R.id.network_input_scroll)).perform(swipeUp());
        Helper.wait(1);
        click(withId(R.id.checkbox_testnet));
        click(withId(R.string.action_add_network));
    }

    public static void importPKWalletFromFrontPage(String privateKey)
    {
        click(withText("I already have a Wallet"));
        click(withText("Private key"));
        Helper.wait(1);
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(R.id.input_private_key)))))).perform(replaceText(privateKey));
        Helper.wait(1); // Avoid error: Error performing a ViewAction! soft keyboard dismissal animation may have been in the way. Retrying once after: 1000 millis
        click(withId(R.id.import_action_pk));
        Helper.wait(15);
    }

    public static void importKSWalletFromFrontPage(String keystore, String password)
    {
        click(withText("I already have a Wallet"));
        click(withText("Keystore"));
        Helper.wait(1);
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(R.id.input_keystore)))))).perform(replaceText(keystore));
        Helper.wait(1); // Avoid error: Error performing a ViewAction! soft keyboard dismissal animation may have been in the way. Retrying once after: 1000 millis
        click(withText("Continue"));
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(R.id.input_password)))))).perform(replaceText(password));
        click(withText("Continue"));
        Helper.wait(10);
    }

    public static void importKSWalletFromSettingsPage(String keystore, String password)
    {
        gotoSettingsPage();
        click(withText("Change / Add Wallet"));
        click(withId(R.id.action_add));
        click(withId(R.id.import_account_action));
        click(withText("Keystore"));
        Helper.wait(1);
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(R.id.input_keystore)))))).perform(replaceText(keystore));
        Helper.wait(1); // Avoid error: Error performing a ViewAction! soft keyboard dismissal animation may have been in the way. Retrying once after: 1000 millis
        click(withText("Continue"));
        onView(CoreMatchers.allOf(withId(R.id.edit_text), withParent(withParent(withParent(withId(R.id.input_password)))))).perform(replaceText(password));
        click(withText("Continue"));
        Helper.wait(5);
        shouldSee("Select Active Networks");
        pressBack();
    }

    public static void gotoWalletPage()
    {
        click(withId(R.id.nav_wallet_text));
    }

    public static void gotoSettingsPage()
    {
        click(withId(R.id.nav_settings_text));
    }

    public static void toggleSwitch(int id)
    {
        onView(CoreMatchers.allOf(withId(R.id.switch_material), isDescendantOfA(withId(id)))).perform(ViewActions.click());
    }

    public static void addNewNetwork(String name, String symbol, String url)
    {
        gotoSettingsPage();
        selectMenu("Select Active Networks"); //action_add
        //onView(withContentDescription(R.string.action_add)).perform(ViewActions.click());
        //click(withText("Add"));
        //onView(withId(R.id.action_add)).perform(ViewActions.click());
        try {
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
        } catch (Exception e) {
            //This is normal. Maybe we dont have overflow menu.
        }
        onView(CoreMatchers.anyOf(withText(R.string.action_add), withId(R.id.action_add))).perform(ViewActions.click());
        //click(withId(R.id.action_add));
        input(R.id.input_network_name, name);
        input(R.id.input_network_rpc_url, url);
        input(R.id.input_network_chain_id, "2");
        input(R.id.input_network_symbol, symbol);
        input(R.id.input_network_explorer_api, url);
        input(R.id.input_network_block_explorer_url, url);
        onView(withId(R.id.network_input_scroll)).perform(swipeUp());
        Helper.wait(1);
        click(withId(R.id.checkbox_testnet));
        click(withId(R.string.action_add_network));
        pressBack();
    }

    public static void input(int id, String text)
    {
        onView(CoreMatchers.allOf(withId(R.id.edit_text), isDescendantOfA(withId(id)))).perform(replaceText(text));
    }

    public static void watchWalletWithENS(String ens)
    {
        click(withText("I already have a Wallet"));
        click(withText("Private key")); // Scroll to right
        Helper.wait(1);
        click(withText("Watch-only Wallets"));
        Helper.wait(1);
        input(R.id.input_watch_address, ens);
        Helper.wait(5);
        click(withText("Watch Wallet"));
    }

    public static void selectCurrency(String currency)
    {
        gotoSettingsPage();
        selectMenu("Change Currency");
        Helper.wait(1);
        clickListItem(R.id.list, withText(currency));
        Helper.wait(1);
        pressBack();
    }

    public static void openOptionsMenu()
    {
        openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
    }

    public static void addCustomToken(String contractAddress)
    {
        //add the token manually since test doesn't seem to work normally
        click(withId(R.id.action_my_wallet));
        click(withSubstring("Add / Hide Tokens"));
        Helper.wait(1);
        click(withId(R.id.action_add));
        Helper.wait(1);

        onView(CoreMatchers.allOf(withId(R.id.edit_text))).perform(replaceText(contractAddress));

        onView(isRoot()).perform(waitUntil(withId(R.id.select_token), 300));

        click(withId(R.id.select_token));

        click(withSubstring("Save"));

        Helper.wait(1);

        if (hasView("Add / Hide Tokens"))
        {
            pressBack();
            Helper.wait(1);
            waitUntil(withId(R.id.nav_settings_text), 30);
        }

        //pressBack();

        //Swipe up
        onView(withId(R.id.coordinator)).perform(ViewActions.swipeUp());
    }
}
