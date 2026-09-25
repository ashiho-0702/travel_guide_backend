package com.study.travel_guide.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.study.travel_guide.common.BizException;
import com.study.travel_guide.entity.Trip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;

@Slf4j
@Service
public class TripPdfService {

    private final TripService tripService;
    private final JsonMapper jsonMapper;

    @Value("${pdf.font-path:}")
    private String fontPath;

    public TripPdfService(TripService tripService, JsonMapper jsonMapper) {
        this.tripService = tripService;
        this.jsonMapper = jsonMapper;
    }

    public byte[] exportPdf(Long userId, Long id) {
        Trip trip = tripService.detail(userId, id);
        JsonNode guide = parseResult(trip.getResult());
        String html = buildHtml(trip, guide);
        return htmlToPdf(html);
    }

    private JsonNode parseResult(String result) {
        try {
            return jsonMapper.readTree(result);
        } catch (Exception e) {
            log.warn("攻略 result 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildHtml(Trip trip, JsonNode guide) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>");
        sb.append("<style>");
        sb.append("body{font-family:'SimHei',sans-serif;font-size:12px;color:#333;}");
        sb.append("h1{text-align:center;}");
        sb.append("h2{margin-top:16px;border-bottom:1px solid #999;padding-bottom:4px;}");
        sb.append("table{width:100%;border-collapse:collapse;margin:8px 0;}");
        sb.append("th,td{border:1px solid #999;padding:6px;text-align:left;}");
        sb.append("th{background:#f0f0f0;}");
        sb.append("</style></head><body>");

        String city = trip.getCity() == null ? "" : trip.getCity();
        Integer days = trip.getDays() == null ? 0 : trip.getDays();
        sb.append("<h1>").append(esc(city)).append(' ').append(days).append("天 旅行攻略</h1>");

        if (guide != null) {
            if (guide.has("overview")) {
                sb.append("<p><b>行程概览：</b>").append(esc(guide.path("overview").asText())).append("</p>");
            }
            if (guide.has("estimatedTotalCost")) {
                sb.append("<p><b>全程预估费用：</b>").append(guide.path("estimatedTotalCost").asText()).append(" 元</p>");
            }
            JsonNode daysNode = guide.path("days");
            if (daysNode.isArray()) {
                for (JsonNode day : daysNode) {
                    appendDay(sb, day);
                }
            }
            JsonNode tips = guide.path("tips");
            if (tips.isArray() && tips.size() > 0) {
                sb.append("<h2>整体避坑建议</h2><ul>");
                for (JsonNode tip : tips) {
                    sb.append("<li>").append(esc(tip.asText())).append("</li>");
                }
                sb.append("</ul>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendDay(StringBuilder sb, JsonNode day) {
        sb.append("<h2>第 ").append(day.path("day").asText()).append(" 天");
        if (day.has("title") && !day.path("title").asText().isBlank()) {
            sb.append("：").append(esc(day.path("title").asText()));
        }
        sb.append("</h2>");

        JsonNode spots = day.path("spots");
        if (spots.isArray() && spots.size() > 0) {
            sb.append("<table><tr><th>景点</th><th>推荐理由</th><th>游玩时长</th><th>到达方式</th><th>费用(元)</th></tr>");
            for (JsonNode spot : spots) {
                sb.append("<tr>");
                sb.append("<td>").append(esc(spot.path("name").asText())).append("</td>");
                sb.append("<td>").append(esc(spot.path("reason").asText())).append("</td>");
                sb.append("<td>").append(esc(spot.path("duration").asText())).append("</td>");
                sb.append("<td>").append(esc(spot.path("transport").asText())).append("</td>");
                sb.append("<td>").append(spot.has("estimatedCostCny") ? spot.path("estimatedCostCny").asText() : "-").append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        JsonNode food = day.path("food");
        if (food.isArray() && food.size() > 0) {
            sb.append("<table><tr><th>美食</th><th>推荐理由</th><th>费用(元)</th></tr>");
            for (JsonNode f : food) {
                sb.append("<tr>");
                sb.append("<td>").append(esc(f.path("name").asText())).append("</td>");
                sb.append("<td>").append(esc(f.path("reason").asText())).append("</td>");
                sb.append("<td>").append(f.has("estimatedCostCny") ? f.path("estimatedCostCny").asText() : "-").append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        if (day.has("estimatedCostCny")) {
            sb.append("<p><b>当天预估费用：</b>").append(day.path("estimatedCostCny").asText()).append(" 元</p>");
        }
        if (day.has("note") && !day.path("note").asText().isBlank()) {
            sb.append("<p><b>注意事项：</b>").append(esc(day.path("note").asText())).append("</p>");
        }
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private byte[] htmlToPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            File font = findFont();
            if (font != null) {
                builder.useFont(font, "SimHei");
            }
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成 PDF 失败: {}", e.getMessage(), e);
            throw new BizException(500, "PDF 生成失败");
        }
    }

    private File findFont() {
        if (fontPath != null && !fontPath.isBlank()) {
            File f = new File(fontPath);
            if (f.exists()) {
                return f;
            }
        }
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) {
                return f;
            }
        }
        log.warn("未找到中文字体，PDF 中文可能乱码（可在 application.yml 配置 pdf.font-path 指定）");
        return null;
    }
}
