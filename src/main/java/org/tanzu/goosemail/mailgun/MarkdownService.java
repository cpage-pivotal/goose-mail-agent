package org.tanzu.goosemail.mailgun;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
public class MarkdownService {

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: #24292e;
                        max-width: 800px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    pre {
                        background-color: #f6f8fa;
                        border-radius: 6px;
                        padding: 16px;
                        overflow-x: auto;
                    }
                    code {
                        background-color: #f6f8fa;
                        border-radius: 3px;
                        padding: 0.2em 0.4em;
                        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
                        font-size: 85%%;
                    }
                    pre code {
                        background-color: transparent;
                        padding: 0;
                    }
                    blockquote {
                        border-left: 4px solid #dfe2e5;
                        margin: 0;
                        padding-left: 16px;
                        color: #6a737d;
                    }
                    h1, h2, h3 {
                        border-bottom: 1px solid #eaecef;
                        padding-bottom: 0.3em;
                    }
                    a {
                        color: #0366d6;
                        text-decoration: none;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%%;
                    }
                    th, td {
                        border: 1px solid #dfe2e5;
                        padding: 8px 12px;
                    }
                    th {
                        background-color: #f6f8fa;
                    }
                </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().build();
    }

    public String convertToHtml(String markdown) {
        Node document = parser.parse(markdown);
        String htmlContent = renderer.render(document);
        return HTML_TEMPLATE.formatted(htmlContent);
    }
}
