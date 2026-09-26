package nz.kiwifinance.sandbox;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A minimal consent screen standing in for Akahu's OAuth page in the sandbox.
 */
@Profile("sandbox")
@RestController
class SandboxOAuthController {

    @GetMapping(value = "/sandbox/akahu/oauth", produces = MediaType.TEXT_HTML_VALUE)
    String consent(
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam String state,
            @RequestParam(value = "client_id", required = false) String clientId) {
        String approve = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", SandboxAkahuController.OAUTH_CODE)
                .queryParam("state", state)
                .encode()
                .toUriString();
        String deny = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", "access_denied")
                .queryParam("state", state)
                .encode()
                .toUriString();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Akahu sandbox</title>
                  <style>
                    body { font-family: system-ui, sans-serif; background: #0f172a; color: #e2e8f0; display: grid; place-items: center; min-height: 100vh; margin: 0; }
                    main { background: #1e293b; padding: 32px; border-radius: 20px; max-width: 420px; }
                    h1 { margin-top: 0; font-size: 22px; }
                    a { display: block; text-align: center; padding: 14px; border-radius: 12px; margin-top: 12px; text-decoration: none; font-weight: 600; }
                    .approve { background: #22c55e; color: #052e16; }
                    .deny { background: transparent; color: #94a3b8; border: 1px solid #334155; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>Akahu sandbox</h1>
                    <p>Kiwi Finance would like read-only access to your sandbox accounts and transactions.</p>
                    <a class="approve" href="%s">Approve access</a>
                    <a class="deny" href="%s">Cancel</a>
                  </main>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(approve), HtmlUtils.htmlEscape(deny));
    }
}
