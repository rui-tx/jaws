package unit;

import org.junit.jupiter.api.Test;
import org.ruitx.jaws.commands.RenderPartialCommand;
import org.ruitx.jaws.components.Hermes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ruitx.jaws.configs.ApplicationConfig.PORT;

public class HermesUnitTests {

    @Test
    public void givenUsernameParameter_whenParsed_thenUsernameIsReplaced() throws IOException {
        String input = "<div>Username: {{username}}</div>";
        Map<String, String> queryParams = Map.of("username", "JohnDoe");
        Map<String, String> bodyParams = Map.of();

        String expected = "<div>Username: JohnDoe</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenPriceParameterWithDollarSign_whenParsed_thenPriceIsReplaced() throws IOException {
        String input = "<div>Price: {{price}}</div>";
        Map<String, String> queryParams = Map.of("price", "$10.00");
        Map<String, String> bodyParams = Map.of();

        String expected = "<div>Price: $10.00</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenValueParameterWithDollarSign_whenParsed_thenValueIsReplaced() throws IOException {
        String input = "<div>Value: {{value}}</div>";
        Map<String, String> queryParams = Map.of("value", "$10.00");
        Map<String, String> bodyParams = Map.of();

        String expected = "<div>Value: $10.00</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenMultipleParameters_whenParsed_thenParametersAreReplaced() throws IOException {
        String input = "<div>{{username}} owes {{amount}} dollars</div>";
        Map<String, String> queryParams = Map.of(
                "username", "JohnDoe",
                "amount", "$50"
        );
        Map<String, String> bodyParams = Map.of();

        String expected = "<div>JohnDoe owes $50 dollars</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenNoPlaceholders_whenParsed_thenOriginalHTMLIsReturned() throws IOException {
        String input = "<div>Hello World</div>";
        Map<String, String> queryParams = Map.of();
        Map<String, String> bodyParams = Map.of();

        String expected = "<div>Hello World</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenPartialFile_whenRendered_thenPartialContentIsRendered() throws IOException {
        RenderPartialCommand renderPartialCommand = new RenderPartialCommand();
        String command = String.format("renderPartial(\"%s\")", "docs/empty.html");
        String partialResult = renderPartialCommand.execute(command);

        String expectedOutput = String.format("""
                <div id="tests">
                    <article>
                        <p>This page is for tests.</p>
                        <!-- And yes, I'm using this page for a test (for now)... {}.$^*+?|[]()\\ -->
                    </article>
                    getServerPort: %s
                </div>""", PORT);

        assertEquals(expectedOutput, partialResult);
    }

    @Test
    public void givenInvalidCommandInHTML_whenParseHTMLWithParams_thenLeaveUnchanged() throws IOException {
        String input = "<div>{{invalid_command(}}</div>";
        Map<String, String> queryParams = new LinkedHashMap<>();
        Map<String, String> bodyParams = new LinkedHashMap<>();

        String expected = "<div>{{invalid_command(}}</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenHTMLWithMultipleCommands_whenParseHTMLWithParams_thenAllCommandsExecute() throws IOException {
        String input = "<div>Port: {{getServerPort()}} | {{renderPartial(\"docs/empty.html\")}}</div>";
        Map<String, String> queryParams = new LinkedHashMap<>();
        Map<String, String> bodyParams = new LinkedHashMap<>();

        String expected = String.format("""
                <div>Port: %s | <div id="tests">
                    <article>
                        <p>This page is for tests.</p>
                        <!-- And yes, I'm using this page for a test (for now)... {}.$^*+?|[]()\\ -->
                    </article>
                    getServerPort: %s
                </div></div>""", PORT, PORT);
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }

    @Test
    public void givenHTMLWithMixedPlaceholdersAndCommands_whenParseHTMLWithParams_thenAllReplacedCorrectly() throws IOException {
        String input = "<div>Welcome, {{username}}! Your balance is {{balance}}. Btw the jaws port is {{getServerPort()}}</div>";
        Map<String, String> queryParams = Map.of("username", "JaneDoe");
        Map<String, String> bodyParams = Map.of("balance", "$75.00");

        String expected = "<div>Welcome, JaneDoe! Your balance is $75.00. Btw the jaws port is 15000</div>";
        String result = Hermes.parseHTML(input, queryParams, bodyParams);

        assertEquals(expected, result);
    }
}



