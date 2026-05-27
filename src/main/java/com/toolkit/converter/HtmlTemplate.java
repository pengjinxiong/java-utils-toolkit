package com.toolkit.converter;

/**
 * 生成兼容 Java HTML-to-PDF 库（Flying Saucer / OpenHTMLToPDF）的 HTML 模板
 */
public class HtmlTemplate {

    /**
     * 生成完整的 HTML 页面
     */
    public static String wrap(String title, String bodyContent) {
        return """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                    <title>%s</title>
                    <style type="text/css">
                        body {
                            font-family: SimSun;
                            font-size: 12pt;
                            line-height: 1.5;
                            margin: 0;
                            padding: 20px;
                            color: black;
                        }
                
                        .container {
                            width: 100%%;
                            margin: 0 auto;
                        }
                
                        h1 {
                            font-size: 16pt;
                            font-weight: bold;
                            text-align: center;
                            margin: 20px 0;
                        }
                
                        h2 {
                            font-size: 14pt;
                            font-weight: bold;
                            text-align: left;
                            margin: 20px 0;
                        }
                
                        h3 {
                            font-size: 13pt;
                            font-weight: bold;
                            margin-top: 15px;
                            margin-bottom: 8px;
                        }
                
                        p {
                            font-size: 12pt;
                            margin: 8px 0;
                        }

                        .param-placeholder {
                            color: red;
                            text-decoration: underline;
                        }
                
                        table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin: 15px 0;
                            font-size: 12pt;
                        }
                
                        th, td {
                            border: 1px solid black;
                            padding: 6px;
                            text-align: left;
                            vertical-align: top;
                        }
                
                        th {
                            font-weight: bold;
                            background-color: #f0f0f0;
                        }
                
                        .bold {
                            font-weight: bold;
                        }
                
                        .no-indent {
                            text-indent: 0;
                        }
                
                        .indent-list {
                            margin-left: 36pt;
                            margin-top: 5px;
                            margin-bottom: 5px;
                        }
                
                        .signature-area {
                            margin-top: 50px;
                            page-break-inside: avoid;
                        }
                
                        .col-label {
                            width: 30%%;
                            font-weight: bold;
                            background-color: #f0f0f0;
                        }
                
                        .col-content {
                            width: 70%%;
                        }
                
                        .center {
                            text-align: center;
                        }
                
                        .right {
                            text-align: right;
                        }
                
                        ul, ol {
                            margin: 8px 0;
                            padding-left: 36pt;
                        }
                
                        li {
                            margin: 4px 0;
                        }
                
                        .page-break {
                            page-break-before: always;
                        }
                    </style>
                </head>
                <body>
                <div class="container">
                %s
                </div>
                </body>
                </html>
                """.formatted(escapeHtml(title), bodyContent);
    }

    /**
     * HTML 特殊字符转义
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
