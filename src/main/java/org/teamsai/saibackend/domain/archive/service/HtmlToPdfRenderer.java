package org.teamsai.saibackend.domain.archive.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

@Slf4j
@Component
public class HtmlToPdfRenderer {

    public byte[] render(String html, String errorContext) {
        ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();

            builder.useFont(
                    () -> getClass().getResourceAsStream("/static/font/pretendard/Pretendard-Regular.ttf"),
                    "Pretendard", 400, BaseRendererBuilder.FontStyle.NORMAL, true
            );
            builder.useFont(
                    () -> getClass().getResourceAsStream("/static/font/pretendard/Pretendard-Bold.ttf"),
                    "Pretendard", 700, BaseRendererBuilder.FontStyle.NORMAL, true
            );

            builder.useDefaultPageSize(210, 297, BaseRendererBuilder.PageSizeUnits.MM);
            builder.withHtmlContent(html, "");
            builder.toStream(pdfBuffer);
            builder.run();

        } catch (Exception e) {
            log.error("PDF 생성 실패 - {}", errorContext, e);
            throw new RuntimeException("PDF 생성 중 오류가 발생했습니다.", e);
        }

        return pdfBuffer.toByteArray();
    }
}
