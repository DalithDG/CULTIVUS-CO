package com.example.demo.services;

import com.example.demo.Config.AsyncConfig;
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
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Envío del correo de recuperación.
 *
 * - Se ejecuta en segundo plano para no filtrar por tiempos de respuesta si el
 *   correo existe o no.
 * - Nunca registra el token, la contraseña ni las credenciales SMTP.
 * - Si SMTP no está configurado, no rompe la petición: deja un aviso y sigue.
 * - El enlace se construye con APP_BASE_URL, nunca con datos del usuario.
 */
@Service
public class PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

    private static final String BASE_URL_POR_DEFECTO = "http://localhost:8080";
    private static final String REMITENTE_POR_DEFECTO = "no-reply@cultivus.co";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String username;
    private final String password;
    private final String remitente;
    private final String baseUrl;

    public PasswordResetMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${cultivus.mail.from:}") String remitente,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host;
        this.username = username;
        this.password = password;
        this.remitente = StringUtils.hasText(remitente)
                ? remitente.trim()
                : (StringUtils.hasText(username) ? username.trim() : REMITENTE_POR_DEFECTO);
        this.baseUrl = normalizarBaseUrl(baseUrl);

        if (!StringUtils.hasText(this.host)) {
            log.warn("SMTP de recuperación NO configurado (MAIL_HOST vacío). "
                    + "No se enviarán correos hasta definir MAIL_HOST, MAIL_USERNAME y MAIL_PASSWORD.");
        } else {
            log.info("SMTP de recuperación configurado: host={}, usuarioConfigurado={}, passwordConfigurado={}",
                    this.host, StringUtils.hasText(this.username), StringUtils.hasText(this.password));
        }
        if (this.baseUrl.contains("localhost") || this.baseUrl.contains("127.0.0.1")) {
            log.warn("APP_BASE_URL apunta a localhost ({}). Los enlaces del correo no funcionarán "
                    + "desde otro dispositivo ni en Render.", this.baseUrl);
        }
    }

    /**
     * Envía el enlace. El nombre del usuario se escapa antes de insertarlo en
     * el HTML. Cualquier fallo del proveedor se registra sin datos sensibles.
     */
    @Async(AsyncConfig.PASSWORD_RESET_MAIL_EXECUTOR)
    public void enviarEnlace(String destinatario, String nombre, String tokenOriginal) {
        if (!StringUtils.hasText(host)) {
            log.warn("SMTP no configurado (MAIL_HOST vacío): no se envía el correo de recuperación");
            return;
        }
        if (StringUtils.hasText(username) && !StringUtils.hasText(password)) {
            log.warn("SMTP sin contraseña configurada: no se envía el correo de recuperación");
            return;
        }
        if (!StringUtils.hasText(destinatario) || !StringUtils.hasText(tokenOriginal)) {
            log.warn("Datos incompletos: no se envía el correo de recuperación");
            return;
        }

        JavaMailSender mailSender;
        try {
            mailSender = mailSenderProvider.getIfAvailable();
        } catch (Exception e) {
            log.error("No se pudo inicializar el cliente SMTP ({})", e.getClass().getSimpleName());
            return;
        }
        if (mailSender == null) {
            log.warn("No hay JavaMailSender disponible: no se envía el correo de recuperación");
            return;
        }

        String enlace = construirEnlace(tokenOriginal);
        String nombreLimpio = StringUtils.hasText(nombre) ? nombre.trim() : "productor";
        String nombreSeguro = HtmlUtils.htmlEscape(nombreLimpio);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject("Restablece tu contraseña - Cultivus");
            helper.setText(textoPlano(nombreLimpio, enlace), html(nombreSeguro, enlace));
            mailSender.send(message);
            log.info("Correo de recuperación enviado correctamente");
        } catch (Exception e) {
            // No se registra el destinatario, el token ni las credenciales.
            log.error("No se pudo enviar el correo de recuperación ({} / {})",
                    e.getClass().getSimpleName(), causaSimple(e));
        }
    }

    String construirEnlace(String tokenOriginal) {
        String tokenCodificado = URLEncoder.encode(tokenOriginal, StandardCharsets.UTF_8);
        return baseUrl + "/usuario/restablecer?token=" + tokenCodificado;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    private static String causaSimple(Throwable error) {
        Throwable causa = error != null ? error.getCause() : null;
        return causa != null ? causa.getClass().getSimpleName() : "sin causa";
    }

    private String textoPlano(String nombre, String enlace) {
        return "Hola " + nombre + ",\n\n"
                + "Recibimos una solicitud para restablecer la contraseña de tu cuenta en Cultivus.\n"
                + "Abre este enlace dentro de los próximos 15 minutos:\n\n"
                + enlace + "\n\n"
                + "El enlace se puede usar una sola vez. Si no solicitaste este cambio, "
                + "ignora este mensaje: tu contraseña actual seguirá funcionando.\n\n"
                + "Nunca te pediremos tu contraseña por correo.\n\n"
                + "Equipo Cultivus\n";
    }

    private String html(String nombreSeguro, String enlace) {
        String enlaceSeguro = HtmlUtils.htmlEscape(enlace);
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#111827;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f4f6;padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<tr><td style=\"background:#2d6a1f;padding:22px 28px;color:#ffffff;\">"
                + "<div style=\"font-size:22px;font-weight:bold;\">Cultivus</div>"
                + "<div style=\"font-size:13px;opacity:.9;\">Tu mercado agrícola en línea</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px;\">"
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#111827;\">Restablece tu contraseña</h1>"
                + "<p style=\"margin:0 0 14px;font-size:14px;line-height:1.6;color:#374151;\">Hola "
                + nombreSeguro + ",</p>"
                + "<p style=\"margin:0 0 18px;font-size:14px;line-height:1.6;color:#374151;\">"
                + "Recibimos una solicitud para restablecer la contraseña de tu cuenta. "
                + "Este enlace es válido durante <strong>15 minutos</strong> y solo se puede usar una vez.</p>"
                + "<p style=\"margin:0 0 24px;text-align:center;\">"
                + "<a href=\"" + enlaceSeguro + "\" style=\"display:inline-block;background:#2d6a1f;color:#ffffff;"
                + "text-decoration:none;font-weight:bold;font-size:14px;padding:13px 22px;border-radius:8px;\">"
                + "Crear nueva contraseña</a></p>"
                + "<p style=\"margin:0 0 12px;font-size:12px;line-height:1.6;color:#6b7280;\">"
                + "Si el botón no funciona, copia y pega este enlace en tu navegador:</p>"
                + "<p style=\"margin:0 0 20px;font-size:12px;line-height:1.5;word-break:break-all;color:#2d6a1f;\">"
                + enlaceSeguro + "</p>"
                + "<p style=\"margin:0;font-size:12px;line-height:1.6;color:#6b7280;\">"
                + "Si no solicitaste el cambio, ignora este mensaje: tu contraseña actual seguirá funcionando. "
                + "Nunca te pediremos tu contraseña por correo.</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:14px 28px;background:#f9fafb;border-top:1px solid #e5e7eb;"
                + "font-size:11px;color:#6b7280;\">Cultivus Co · Conectamos el campo con tu mesa</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /**
     * Solo acepta http/https. Si APP_BASE_URL está mal configurada se usa el
     * valor local seguro en lugar de propagar un esquema peligroso al correo.
     */
    static String normalizarBaseUrl(String valor) {
        if (!StringUtils.hasText(valor)) {
            return BASE_URL_POR_DEFECTO;
        }
        String limpio = valor.trim();
        while (limpio.endsWith("/")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        try {
            URI uri = URI.create(limpio);
            String esquema = uri.getScheme();
            if (("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null) {
                return limpio;
            }
        } catch (IllegalArgumentException ignored) {
            // Se usa el valor por defecto.
        }
        log.warn("APP_BASE_URL no es una URL http/https válida; se usa el valor local por defecto");
        return BASE_URL_POR_DEFECTO;
    }
}
