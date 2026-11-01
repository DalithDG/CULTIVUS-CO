package com.example.demo.services;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistroMailService {

    private static final Logger log = LoggerFactory.getLogger(RegistroMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String username;
    private final String password;
    private final String remitente;

    @Value("${cultivus.mail.webhook-url:}")
    private String webhookUrl;

    public RegistroMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${cultivus.mail.from:}") String remitente) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        this.username = username;
        this.password = password;
        this.remitente = StringUtils.hasText(remitente) ? remitente.trim()
                : (StringUtils.hasText(username) ? username.trim() : "no-reply@cultivus.co");
    }

    @Async
    public void enviarBienvenida(String destinatario, String nombre, String rol) {
        String asunto = "Bienvenido a Cultivus";
        String texto = "Hola " + nombre + ",\n\nTu registro como " + rol + " en Cultivus fue exitoso.\n\nEquipo Cultivus";
        String html = "<p>Hola " + nombre + ",</p><p>Tu registro como <b>" + rol + "</b> en Cultivus fue exitoso.</p>";
        enviar(destinatario, asunto, texto, html);
    }

    @Async
    public void enviarCodigoVerificacion(String destinatario, String nombre, String codigo) {
        String asunto = "Código de verificación - Cultivus";
        String texto = "Hola " + nombre + ",\n\nTu código de verificación es: " + codigo + "\n\nEquipo Cultivus";
        String html = "<p>Hola " + nombre + ",</p><p>Tu código de verificación es: <b>" + codigo + "</b></p>";
        enviar(destinatario, asunto, texto, html);
    }

    private void enviar(String destinatario, String asunto, String texto, String html) {
        if (!StringUtils.hasText(destinatario)) return;
        // SMTP
        if (StringUtils.hasText(host) && StringUtils.hasText(username) && StringUtils.hasText(password)) {
            try {
                JavaMailSender ms = mailSenderProvider.getIfAvailable();
                if (ms != null) {
                    MimeMessage msg = ms.createMimeMessage();
                    MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
                    h.setFrom(remitente);
                    h.setTo(destinatario);
                    h.setSubject(asunto);
                    h.setText(texto, html);
                    ms.send(msg);
                    log.info("Correo de registro enviado a {}", destinatario);
                    return;
                }
            } catch (Exception e) {
                log.error("Error SMTP registro: {}", e.getMessage());
            }
        }
        log.warn("SMTP no configurado: correo de registro no enviado a {}", destinatario);
    }
}