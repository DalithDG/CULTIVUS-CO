# Plan para Agente IA — Implementación del Widget de Wompi en Cultivus-CO
### (Para ejecutar sobre la versión desplegada / ya actualizada)

---

## CONTEXTO DEL PROYECTO

- **Framework:** Spring Boot 3, Java 17
- **Plantillas:** Thymeleaf
- **Base de datos:** MongoDB
- **Pasarela de pago:** Wompi (Bancolombia), modo Widget
- **Estructura de código:** `src/main/java/com/example/demo/`
- **Plantillas HTML:** `src/main/resources/templates/`
- **Propiedades:** `src/main/resources/application.properties`
- **Entorno objetivo:** Servidor desplegado con URL pública HTTPS

**Instrucción general:** Lee cada paso completamente antes de ejecutarlo. Antes de modificar cualquier archivo existente, léelo primero para entender su estructura. No borres código que no esté relacionado con Wompi.

---

## FASE 1 — EXPLORACIÓN INICIAL

### 1.1 — Leer la estructura del proyecto
Lee y reporta el contenido de:
- `src/main/resources/application.properties`
- La lista de archivos en `src/main/java/com/example/demo/`
- La lista de archivos en `src/main/resources/templates/`

### 1.2 — Identificar archivos existentes relevantes
Busca si ya existen estos archivos. Para cada uno, reporta si existe o no:

```
src/main/java/com/example/demo/services/WompiService.java
src/main/java/com/example/demo/Controller/PagoController.java
src/main/resources/templates/pago.html
src/main/resources/templates/test-wompi.html
src/main/resources/templates/confirmacion-pago.html
```

### 1.3 — Identificar modelos existentes
Busca y lee (si existen):
- El modelo `Carrito.java` — necesitas saber el nombre del método que retorna el total (probablemente `getTotalEstimado()`)
- El modelo `Pedido.java` — necesitas saber su estructura
- El modelo `Usuario.java`
- `DatosPago.java` (o similar en el paquete `embebidos`)
- Los repositorios `CarritoRepository.java` y `PedidoRepository.java`

Reporta los nombres exactos de métodos y campos antes de continuar.

---

## FASE 2 — CONFIGURACIÓN DE PROPIEDADES

### 2.1 — Agregar propiedades de Wompi a `application.properties`

Abre `application.properties` y agrega al final (si no existen ya):

```properties
# ── Wompi Pasarela de Pago ──────────────────────────────────────────
wompi.public-key=${WOMPI_PUBLIC_KEY:}
wompi.private-key=${WOMPI_PRIVATE_KEY:}
wompi.integrity-secret=${WOMPI_INTEGRITY_SECRET:}
wompi.events-secret=${WOMPI_EVENTS_SECRET:}
wompi.currency=${WOMPI_CURRENCY:COP}

# ── Configuración de envío provisional ─────────────────────────────
cultivus.envio.provisional=${CULTIVUS_ENVIO_PROVISIONAL:10000.0}
```

> **Nota para el agente:** No escribas los valores reales de las claves. Deben venir de variables de entorno del servidor.

---

## FASE 3 — CREAR `WompiService.java`

Si el archivo no existe, créalo en:
`src/main/java/com/example/demo/services/WompiService.java`

Si ya existe, léelo primero y verifica que contenga todos estos métodos. Agrega solo lo que falte.

**Contenido completo:**

```java
package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class WompiService {

    private static final Logger log = LoggerFactory.getLogger(WompiService.class);

    @Value("${wompi.public-key:}")
    private String publicKey;

    @Value("${wompi.private-key:}")
    private String privateKey;

    @Value("${wompi.integrity-secret:}")
    private String integritySecret;

    @Value("${wompi.events-secret:}")
    private String eventsSecret;

    @Value("${wompi.currency:COP}")
    private String currency;

    @Value("${cultivus.envio.provisional:10000.0}")
    private double envioProvisionalPorDefecto;

    // Inyección opcional: si el proyecto tiene un servicio de configuración dinámica
    @Autowired(required = false)
    private AppConfigService configService;

    public String getPublicKey() {
        return publicKey;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Obtiene el costo de envío provisional.
     * Usa AppConfigService si está disponible; si no, usa el valor de properties.
     */
    public double obtenerCostoEnvioProvisional() {
        if (configService != null) {
            return configService.obtenerValorDouble("VALOR_ENVIO_PROVISIONAL", envioProvisionalPorDefecto);
        }
        return envioProvisionalPorDefecto;
    }

    /**
     * Convierte un monto en COP a centavos enteros (sin decimales).
     * Usa Math.round para evitar errores de punto flotante.
     */
    public long convertirACentavos(double montoCop) {
        return Math.round(montoCop * 100.0);
    }

    /**
     * Genera una referencia única para cada intento de pago.
     * Formato: CUL-{timestamp}-{sufijoHex}
     */
    public String generarReferenciaUnica() {
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CUL-" + System.currentTimeMillis() + "-" + randomSuffix;
    }

    /**
     * Genera la firma de integridad SHA-256 requerida por Wompi.
     * Fórmula: SHA256(referencia + montoEnCentavos + moneda + secretoIntegridad)
     */
    public String generarFirmaIntegridad(String referencia, long montoCentavos, String moneda) {
        if (integritySecret == null || integritySecret.trim().isEmpty()) {
            log.error("El secreto de integridad de Wompi (WOMPI_INTEGRITY_SECRET) no está configurado.");
            throw new IllegalStateException("Secreto de integridad de Wompi no configurado");
        }

        String cadenaFirma = referencia + montoCentavos + moneda + integritySecret.trim();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cadenaFirma.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error al calcular hash SHA-256 para firma de integridad Wompi", e);
            throw new RuntimeException("Error calculando firma de integridad", e);
        }
    }
}
```

> **Nota para el agente:** Si el proyecto no tiene `AppConfigService`, cambia `@Autowired(required = false) private AppConfigService configService;` por `// (no disponible)` y simplifica `obtenerCostoEnvioProvisional()` para que solo retorne `envioProvisionalPorDefecto`.

---

## FASE 4 — MODIFICAR `PagoController.java`

Lee el archivo completo primero. Debes agregar o verificar estos tres métodos dentro de la clase `PagoController`. **No borres métodos existentes.**

Asegúrate de que existan los siguientes `@Autowired` al inicio de la clase:
```java
@Autowired
private com.example.demo.services.WompiService wompiService;

@Autowired
private CarritoRepository carritoRepository;

@Autowired
private PedidoRepository pedidoRepository;
```

### 4.1 — Método `mostrarPago` (GET /pago)

Busca si ya existe un método `@GetMapping` que retorne la vista `"pago"`. Si existe, agrega las siguientes líneas **antes** del `return "pago"`:

```java
// Cálculo seguro de Wompi en el servidor
double subtotal = carrito.getTotalEstimado();         // ajusta al método real
double costoEnvio = wompiService.obtenerCostoEnvioProvisional();
double totalConEnvio = subtotal + costoEnvio;
long amountInCents = wompiService.convertirACentavos(totalConEnvio);
String reference = wompiService.generarReferenciaUnica();
String currency = wompiService.getCurrency();
String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);

model.addAttribute("amountInCents", amountInCents);
model.addAttribute("reference", reference);
model.addAttribute("signatureIntegrity", signatureIntegrity);
model.addAttribute("wompiPublicKey", wompiService.getPublicKey());
model.addAttribute("currency", currency);
model.addAttribute("subtotal", subtotal);
model.addAttribute("costoEnvio", costoEnvio);
model.addAttribute("total", totalConEnvio);
```

### 4.2 — Método `iniciarPagoWompi` (POST /pago/iniciar-wompi)

Agrega este método completo si no existe:

```java
/**
 * API: Pre-registra el pedido en estado PENDIENTE y devuelve datos firmados para el Widget de Wompi.
 */
@PostMapping("/iniciar-wompi")
@ResponseBody
public org.springframework.http.ResponseEntity<?> iniciarPagoWompi(
        @RequestBody(required = false) java.util.Map<String, String> payload,
        HttpSession session) {
    try {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
        if (usuario == null) {
            return org.springframework.http.ResponseEntity.status(401).body(
                    java.util.Map.of("ok", false, "error", "Debe iniciar sesión para realizar el pago"));
        }

        Carrito carrito = carritoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Carrito no encontrado"));

        if (carrito.getItems().isEmpty()) {
            return org.springframework.http.ResponseEntity.badRequest().body(
                    java.util.Map.of("ok", false, "error", "Su carrito está vacío"));
        }

        // Cálculo seguro del total en centavos
        double subtotal = carrito.getTotalEstimado();     // ajusta al método real
        double costoEnvio = wompiService.obtenerCostoEnvioProvisional();
        double totalConEnvio = subtotal + costoEnvio;
        long amountInCents = wompiService.convertirACentavos(totalConEnvio);

        // Reutilizar referencia del frontend o generar una nueva
        String reference = (payload != null
                && payload.containsKey("reference")
                && !payload.get("reference").trim().isEmpty())
                ? payload.get("reference").trim()
                : wompiService.generarReferenciaUnica();

        String currency = wompiService.getCurrency();
        String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);
        String direccionEnvio = (payload != null && payload.containsKey("direccionEnvio"))
                ? payload.get("direccionEnvio") : "";

        // ── Aquí agrega la lógica de creación de Pedido(s) PENDIENTE según el modelo del proyecto ──
        // Ejemplo mínimo (adaptar a la estructura real de Pedido del proyecto):
        //
        // DatosPago datosPago = new DatosPago("WOMPI", totalConEnvio, amountInCents, reference, signatureIntegrity);
        // datosPago.setEstado("PENDIENTE");
        // Pedido pedido = new Pedido(comprador, vendedor, direccion, items, datosPago);
        // pedido.setEstado("PENDIENTE");
        // pedidoRepository.save(pedido);
        //
        // IMPORTANTE: adapta esta sección al constructor real de Pedido en el proyecto.

        return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                "ok", true,
                "reference", reference,
                "amountInCents", amountInCents,
                "currency", currency,
                "signatureIntegrity", signatureIntegrity,
                "publicKey", wompiService.getPublicKey()
        ));

    } catch (Exception e) {
        return org.springframework.http.ResponseEntity.badRequest().body(
                java.util.Map.of("ok", false, "error", "Error al iniciar pago: " + e.getMessage()));
    }
}
```

> **Nota para el agente:** El bloque de creación de `Pedido` dentro de `iniciarPagoWompi` debe adaptarse al constructor real de `Pedido.java` que encuentres en el proyecto. Lee ese modelo antes de completar esta sección.

### 4.3 — Método `resultadoPago` (GET /pago/resultado)

Agrega este método si no existe:

```java
/**
 * Retorno tras interacción con el Widget de Wompi.
 */
@GetMapping("/resultado")
public String resultadoPago(
        @RequestParam(value = "reference", required = false) String reference,
        @RequestParam(value = "id", required = false) String transactionId,
        HttpSession session,
        RedirectAttributes redirectAttributes) {

    Usuario usuario = (Usuario) session.getAttribute("usuarioLogueado");
    if (usuario == null) {
        return "redirect:/usuario/login";
    }

    java.util.List<Pedido> pedidos = (reference != null && !reference.trim().isEmpty())
            ? pedidoRepository.findByPago_Referencia(reference.trim())
            : java.util.Collections.emptyList();

    if (pedidos.isEmpty()) {
        redirectAttributes.addFlashAttribute("error",
                "No se encontró el pedido correspondiente a la transacción.");
        return "redirect:/";
    }

    if (transactionId != null && !transactionId.trim().isEmpty()) {
        for (Pedido p : pedidos) {
            if (p.getPago() != null) {
                p.getPago().setTransaccionId(transactionId);
                pedidoRepository.save(p);
            }
        }
    }

    // Limpiar carrito tras confirmar retorno de Wompi
    carritoRepository.findByUsuarioId(usuario.getId()).ifPresent(carritoRepository::delete);

    redirectAttributes.addFlashAttribute("mensaje",
            "Transacción registrada. Pedido #" + pedidos.get(0).getId());
    return "redirect:/pago/confirmacion/" + pedidos.get(0).getId();
}
```

> **Nota para el agente:** Verifica que `PedidoRepository` tenga el método `findByPago_Referencia(String referencia)`. Si no existe, agrégalo a la interfaz del repositorio:
> ```java
> java.util.List<Pedido> findByPago_Referencia(String referencia);
> ```

---

## FASE 5 — CREAR `test-wompi.html`

Crea el archivo `src/main/resources/templates/test-wompi.html` con este contenido exacto.
Este archivo sirve para verificar la integración antes de probar el flujo completo.

```html
<!DOCTYPE html>
<html lang="es" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Prueba Widget Wompi - Cultivus</title>
    <style>
        body { font-family: sans-serif; background: #f4f6f9; padding: 40px; }
        .box { max-width: 650px; margin: auto; background: white; padding: 30px;
               border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 10px; border: 1px solid #ddd; text-align: left; font-size: 14px; }
        th { background: #f8f9fa; width: 35%; }
        .btn { background: #27ae60; color: white; padding: 12px 24px; font-size: 16px;
               font-weight: 600; border-radius: 8px; border: none; cursor: pointer; margin-top: 15px; }
        .result { margin-top: 20px; padding: 15px; background: #eef2ff;
                  border-left: 4px solid #4f46e5; display: none; border-radius: 8px; }
    </style>
</head>
<body>
    <div class="box">
        <h2 style="color:#27ae60;">🧪 Prueba Mínima Widget Wompi (Sandbox)</h2>
        <p style="color:#666; font-size:14px;">
            Valida que las claves y la firma SHA-256 estén correctas. Al presionar el botón se abre el Widget oficial.
        </p>
        <table>
            <tr><th>Llave Pública</th><td th:text="${publicKey}"></td></tr>
            <tr><th>Referencia</th><td th:text="${reference}"></td></tr>
            <tr><th>Monto (COP)</th><td th:text="'$' + ${#numbers.formatDecimal(montoCop, 0, 'COMMA', 0, 'POINT')}"></td></tr>
            <tr><th>Monto (centavos)</th><td th:text="${amountInCents}"></td></tr>
            <tr><th>Moneda</th><td th:text="${currency}"></td></tr>
            <tr><th>Firma SHA-256</th><td style="word-break:break-all;" th:text="${signatureIntegrity}"></td></tr>
        </table>
        <button id="btnTest" class="btn">💳 Abrir Widget de Wompi</button>
        <div id="resultado" class="result">
            <h4>Resultado:</h4>
            <pre id="resultadoTexto" style="font-size:13px; margin:0;"></pre>
        </div>
    </div>

    <script type="text/javascript" src="https://checkout.wompi.co/widget.js"></script>
    <script th:inline="javascript">
        /*<![CDATA[*/
        const publicKey         = /*[[${publicKey}]]*/ '';
        const currency          = /*[[${currency}]]*/ 'COP';
        const amountInCents     = /*[[${amountInCents}]]*/ 1000000;
        const reference         = /*[[${reference}]]*/ '';
        const signatureIntegrity = /*[[${signatureIntegrity}]]*/ '';

        document.getElementById('btnTest').addEventListener('click', function () {
            if (typeof WidgetCheckout === 'undefined') {
                alert('Error: No se pudo cargar el script del widget de Wompi. Revisa tu conexión.');
                return;
            }
            console.log('[TEST] Parámetros del widget:', { publicKey, currency, amountInCents, reference, signatureIntegrity });

            const checkout = new WidgetCheckout({
                currency,
                amountInCents,
                reference,
                publicKey,
                signature: { integrity: signatureIntegrity }
                // Sin redirectUrl en la prueba mínima
            });

            checkout.open(function (result) {
                const t = result.transaction;
                document.getElementById('resultado').style.display = 'block';
                document.getElementById('resultadoTexto').textContent = JSON.stringify(t, null, 2);
                if (t && t.status === 'APPROVED') alert('✅ Pago APROBADO: ' + t.id);
                else if (t && t.status === 'DECLINED') alert('❌ Pago DECLINADO');
            });
        });
        /*]]>*/
    </script>
</body>
</html>
```

Agrega el endpoint en `PagoController.java` para servir esta página:

```java
@GetMapping("/test-wompi")
public String mostrarTestWompi(Model model) {
    double montoCop = 10000.0;
    long amountInCents = wompiService.convertirACentavos(montoCop);
    String reference = wompiService.generarReferenciaUnica();
    String currency = wompiService.getCurrency();
    String signatureIntegrity = wompiService.generarFirmaIntegridad(reference, amountInCents, currency);

    model.addAttribute("publicKey", wompiService.getPublicKey());
    model.addAttribute("reference", reference);
    model.addAttribute("montoCop", montoCop);
    model.addAttribute("amountInCents", amountInCents);
    model.addAttribute("currency", currency);
    model.addAttribute("signatureIntegrity", signatureIntegrity);
    return "test-wompi";
}
```

---

## FASE 6 — MODIFICAR `pago.html`

Lee el archivo completo. Busca el bloque `<script th:inline="javascript">` que contiene el handler del formulario de pago. Reemplaza solo ese bloque con lo siguiente:

> **Importante:** Mantén intacto todo el HTML, CSS y Thymeleaf del archivo. Solo reemplaza el contenido del `<script th:inline="javascript">`.

```html
<script type="text/javascript" src="https://checkout.wompi.co/widget.js"></script>

<script th:inline="javascript">
    /*<![CDATA[*/
    let pasarelaSeleccionada = 'WOMPI';

    // Valores pre-renderizados por el servidor (usados como fallback para referencia inicial)
    const serverPublicKey          = /*[[${wompiPublicKey}]]*/ '';
    const serverCurrency           = /*[[${currency}]]*/ 'COP';
    const serverAmountInCents      = parseInt(/*[[${amountInCents}]]*/ 0, 10);
    const serverReference          = /*[[${reference}]]*/ '';
    const serverSignatureIntegrity = /*[[${signatureIntegrity}]]*/ '';

    function selectPasarela(tipo, element) {
        pasarelaSeleccionada = tipo;
        document.querySelectorAll('.metodo-option').forEach(opt => opt.classList.remove('selected'));
        element.classList.add('selected');
        element.querySelector('input[name="tipoPasarela"]').checked = true;

        const btnPagar = document.getElementById('btnPagar');
        const seccionSimulada = document.getElementById('seccion-metodos-simulados');
        if (tipo === 'WOMPI') {
            if (seccionSimulada) seccionSimulada.style.display = 'none';
            btnPagar.innerHTML = '<i class="fa-solid fa-lock"></i> Pagar con Wompi';
        } else {
            if (seccionSimulada) seccionSimulada.style.display = 'block';
            btnPagar.innerHTML = '<i class="fa-solid fa-check"></i> Confirmar Pago Simulado';
        }
    }

    document.getElementById('formPago').addEventListener('submit', async function (e) {
        e.preventDefault();

        // ── Plan B: pasarela simulada ──
        if (pasarelaSeleccionada === 'SIMULADA') {
            const metodoPago = document.querySelector('input[name="metodoPago"]:checked');
            if (!metodoPago) {
                alert('Por favor seleccione un método de pago simulado.');
                return;
            }
            this.submit();
            return;
        }

        // ── Flujo Wompi Widget ──
        if (typeof WidgetCheckout === 'undefined') {
            alert('El script de Wompi no se pudo cargar. Revisa tu conexión o bloqueadores de publicidad.');
            return;
        }

        const btn = document.getElementById('btnPagar');
        const direccionEnvio = document.getElementById('direccionEnvio')
            ? document.getElementById('direccionEnvio').value : '';

        btn.disabled = true;
        btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Procesando...';

        try {
            // PASO A: Registrar pedido en el servidor y obtener datos firmados
            console.log('[Wompi] Iniciando registro del pedido. Referencia previa:', serverReference);

            const resp = await fetch('/pago/iniciar-wompi', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reference: serverReference, direccionEnvio: direccionEnvio })
            });

            if (!resp.ok) {
                const errText = await resp.text();
                throw new Error('Error del servidor (' + resp.status + '): ' + errText);
            }

            const data = await resp.json();
            console.log('[Wompi] Respuesta del servidor:', data);

            if (!data.ok) {
                throw new Error(data.error || 'El servidor rechazó la solicitud de pago.');
            }

            // PASO B: Construir redirectUrl con el dominio real (funciona con HTTPS en producción)
            const redirectUrl = window.location.origin + '/pago/resultado?reference='
                + encodeURIComponent(data.reference);

            console.log('[Wompi] Abriendo widget con parámetros del servidor:', {
                reference:      data.reference,
                amountInCents:  data.amountInCents,
                currency:       data.currency,
                redirectUrl:    redirectUrl
            });

            btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Abriendo Wompi...';

            // PASO C: Abrir el Widget con datos del servidor (no del HTML pre-renderizado)
            const checkout = new WidgetCheckout({
                currency:      data.currency,
                amountInCents: data.amountInCents,
                reference:     data.reference,
                publicKey:     data.publicKey,
                signature: {
                    integrity: data.signatureIntegrity
                },
                redirectUrl: redirectUrl
            });

            checkout.open(function (result) {
                const transaction = result.transaction;
                console.log('[Wompi] Resultado del widget:', transaction);

                if (transaction && transaction.id) {
                    // Transacción procesada → ir a página de resultado
                    window.location.href = '/pago/resultado?reference='
                        + encodeURIComponent(data.reference)
                        + '&id=' + encodeURIComponent(transaction.id);
                } else {
                    // Usuario canceló o cerró el widget
                    btn.disabled = false;
                    btn.innerHTML = '<i class="fa-solid fa-lock"></i> Pagar con Wompi';
                }
            });

        } catch (err) {
            console.error('[Wompi] Error al iniciar el pago:', err);
            alert('No se pudo iniciar el pago: ' + err.message);
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-lock"></i> Pagar con Wompi';
        }
    });
    /*]]>*/
</script>
```

---

## FASE 7 — CHECKLIST DE VERIFICACIÓN

Ejecuta cada punto en orden y reporta el resultado:

### 7.1 — Compilación
```bash
./mvnw compile
```
Debe compilar sin errores. Si hay errores de importación, agrega los `import` faltantes en `PagoController.java`.

### 7.2 — Prueba mínima del widget
1. Accede a `https://TU-DOMINIO.com/pago/test-wompi`
2. Verifica que la tabla muestra la llave pública (no vacía), una referencia y una firma SHA-256 (64 caracteres hex)
3. Haz clic en "Abrir Widget de Wompi" — el modal debe abrirse
4. Usa la tarjeta de prueba: `4242 4242 4242 4242` · cvv `123` · fecha futura · cuotas `1`
5. ✅ Si el widget abre y procesa = configuración correcta

### 7.3 — Prueba del flujo completo
1. Inicia sesión como comprador con carrito no vacío
2. Ve a `/pago`
3. Abre DevTools → Console + Network
4. Haz clic en "Pagar con Wompi"
5. Verifica en Network: `POST /pago/iniciar-wompi` → HTTP 200 → body con `"ok": true`
6. Verifica en Console: log con `reference`, `amountInCents`, `currency`, `redirectUrl` con HTTPS
7. El widget debe abrirse
8. Paga con tarjeta de prueba
9. Debes llegar a `/pago/resultado?reference=...` y luego a `/pago/confirmacion/{id}`

### 7.4 — Verificar en MongoDB
```
db.pedidos.find({ "pago.referencia": "CUL-..." }).pretty()
```
El pedido debe existir con `estado: "PENDIENTE"` o `"APROBADO"`.

---

## ERRORES COMUNES Y SOLUCIONES

| Error | Causa | Solución |
|---|---|---|
| `403` en `checkout.wompi.co` | `redirectUrl` con `localhost` o HTTP | Asegurar que la app esté en HTTPS con dominio público |
| `IllegalStateException: Secreto de integridad no configurado` | `WOMPI_INTEGRITY_SECRET` no está en las variables de entorno del servidor | Configurar la variable en el panel del servidor desplegado |
| `amountInCents: 0` en el log | `carrito.getTotalEstimado()` retorna 0 | Verificar que el carrito tiene items con precio antes de ir a `/pago` |
| `404` en `/pago/iniciar-wompi` | El endpoint no fue agregado al controlador | Verificar FASE 4.2 |
| Firma inválida en el widget | El `WOMPI_INTEGRITY_SECRET` no coincide con el del Dashboard de Wompi | Copiar el secreto exacto desde Wompi Dashboard → Desarrolladores → Llaves |
| `findByPago_Referencia` no existe | El repositorio no tiene ese método | Agregarlo a `PedidoRepository.java` |
| Botón queda congelado | Excepción JS no capturada | El `try/catch` de la FASE 6 lo resuelve; revisar Console para ver el error exacto |

---

## NOTAS FINALES PARA EL AGENTE

1. **Adapta, no copies ciegamente:** Los nombres de métodos (`getTotalEstimado()`, `getItems()`, etc.) deben coincidir con los modelos reales del proyecto. Léelos antes de escribir el código.

2. **Variables de entorno:** Nunca escribas los valores de las claves en el código. Configúralas en el panel del servidor (Render → Environment, Railway → Variables, etc.).

3. **Orden de pruebas:** Siempre prueba `/pago/test-wompi` primero. Si esa falla, el flujo completo fallará también.

4. **Wompi Dashboard:** Las claves de Sandbox están en [comercios.wompi.co](https://comercios.wompi.co) → Desarrolladores → Llaves de API. Hay cuatro: pública, privada, secreto de integridad, secreto de eventos.

5. **Para producción real:** Cambiar todas las claves `test_` → `prod_` y el secreto de integridad de producción. El `redirectUrl` debe ser HTTPS.
