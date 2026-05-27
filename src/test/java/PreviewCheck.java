import java.io.*;
import java.nio.file.*;

public class PreviewCheck {
    private static String simplifyForPreview(String html) {
        String result = html
            .replace("<!DOCTYPE html>", "")
            .replaceAll("<html[^>]*>", "<html>")
            .replaceAll("(?s)<head>.*?</head>",
                "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" +
                "<style>" +
                "body{font-family:SimSun;font-size:12pt;margin:10px;color:black;}" +
                "h1,h2{text-align:center;font-weight:bold;margin:10px 0;}" +
                "p{margin:5px 0;}" +
                ".param-placeholder{color:red;text-decoration:underline;}" +
                "table{border-collapse:collapse;width:100%;}" +
                "td,th{border:1px solid #000;padding:5px;}" +
                "</style></head>")
            .replace("<br/>", "<br>")
            .replaceAll("\\$\\{param\\d+ ! \"\"\\}", "__________")
            .trim();
        return result;
    }

    public static void main(String[] args) throws Exception {
        String html = new String(Files.readAllBytes(Paths.get("/tmp/generated.html")), "UTF-8");
        String r = simplifyForPreview(html);
        System.out.println("=== FIRST 400 CHARS ===");
        System.out.println(r.substring(0, Math.min(400, r.length())));
        System.out.println();
        System.out.println("startsWith <html>: " + r.startsWith("<html>"));
        System.out.println("startsWith <html> : " + r.startsWith("<html "));
        System.out.println("contains <html >: " + r.contains("<html >"));
        System.out.println("contains param-placeholder: " + r.contains("param-placeholder"));
        System.out.println("contains ${param: " + r.contains("${param"));
    }
}
