package com.example.demo.services;

import com.example.demo.Model.Carrito;
import com.example.demo.Model.OfertaVendedor;
import com.example.demo.Model.TarifaEnvio;
import com.example.demo.Model.Usuario;
import com.example.demo.Model.ZonaMunicipio;
import com.example.demo.Model.embebidos.ProductoCarrito;
import com.example.demo.repository.OfertaRepository;
import com.example.demo.repository.TarifaEnvioRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.repository.ZonaMunicipioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio encargado del cálculo de costos de envío basado en zonas logísticas.
 * Tarifa por vendedor = matriz[zona(ciudad vendedor)][zona(ciudad entrega)]
 * Total = suma de las tarifas de cada vendedor involucrado.
 */
@Service
public class CalculoEnvioService {

    private static final Logger log = LoggerFactory.getLogger(CalculoEnvioService.class);

    public static final String CLAVE_TARIFA_DEFECTO = "TARIFA_ENVIO_DEFECTO";
    public static final double TARIFA_DEFECTO_COP = 15000.0;

    @Autowired
    private ZonaMunicipioRepository zonaMunicipioRepository;

    @Autowired
    private TarifaEnvioRepository tarifaEnvioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private OfertaRepository ofertaRepository;

    @Autowired
    private AppConfigService configService;

    public record DesgloseEnvioVendedor(
            String vendedorId,
            String vendedorNombre,
            String ciudadOrigen,
            Integer zonaOrigen,
            String ciudadDestino,
            Integer zonaDestino,
            long tarifaCentavos,
            boolean tarifaPorDefecto
    ) {}

    public record ResultadoCalculoEnvio(
            long totalCentavos,
            Map<String, Long> tarifasPorVendedor,
            Map<String, Integer> zonasOrigenPorVendedor,
            Integer zonaDestino,
            List<DesgloseEnvioVendedor> desglose
    ) {
        public double getTotalCop() {
            return totalCentavos / 100.0;
        }

        public long obtenerTarifaVendedor(String vendedorId) {
            return tarifasPorVendedor != null ? tarifasPorVendedor.getOrDefault(vendedorId, 0L) : 0L;
        }
    }

    public long obtenerTarifaPorDefectoCentavos() {
        double cop = configService != null
                ? configService.obtenerValorDouble(CLAVE_TARIFA_DEFECTO, TARIFA_DEFECTO_COP)
                : TARIFA_DEFECTO_COP;
        return Math.round(cop * 100.0);
    }

    public Optional<ZonaMunicipio> buscarZonaMunicipio(String ciudad) {
        if (ciudad == null || ciudad.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalizado = ZonasLogistica.normalizar(ciudad);
        return zonaMunicipioRepository.findByMunicipioNormalizado(normalizado);
    }

    public Optional<Integer> obtenerZonaPorCiudad(String ciudad) {
        return buscarZonaMunicipio(ciudad).map(ZonaMunicipio::getZonaId);
    }

    public long calcularTarifaEntreZonas(Integer zonaOrigen, Integer zonaDestino) {
        if (zonaOrigen == null || zonaDestino == null) {
            return obtenerTarifaPorDefectoCentavos();
        }
        return tarifaEnvioRepository.findByZonaOrigenAndZonaDestino(zonaOrigen, zonaDestino)
                .map(TarifaEnvio::getValorCentavos)
                .orElseGet(this::obtenerTarifaPorDefectoCentavos);
    }

    /**
     * Calcula la tarifa de envío para un único vendedor hacia una ciudad de entrega.
     */
    public long calcularTarifaVendedor(String vendedorId, String ciudadEntrega) {
        Integer zonaDestino = obtenerZonaPorCiudad(ciudadEntrega).orElse(null);
        Integer zonaOrigen = null;

        if (vendedorId != null) {
            Optional<Usuario> optVendedor = usuarioRepository.findById(vendedorId);
            if (optVendedor.isPresent() && optVendedor.get().getUbicacion() != null) {
                String ciudadVendedor = optVendedor.get().getUbicacion().getCiudad();
                zonaOrigen = obtenerZonaPorCiudad(ciudadVendedor).orElse(null);
            }
        }

        return calcularTarifaEntreZonas(zonaOrigen, zonaDestino);
    }

    /**
     * Calcula las tarifas para todos los vendedores presentes en el carrito hacia una ciudad de destino.
     */
    public ResultadoCalculoEnvio calcularTarifaCarrito(Carrito carrito, String ciudadEntrega) {
        if (carrito == null || carrito.getItems() == null || carrito.getItems().isEmpty()) {
            return new ResultadoCalculoEnvio(0L, Collections.emptyMap(), Collections.emptyMap(), null, Collections.emptyList());
        }

        Set<String> vendedoresIds = new LinkedHashSet<>();
        for (ProductoCarrito item : carrito.getItems()) {
            String vId = item.getVendedorId();
            if (vId == null && item.getOfertaId() != null && ofertaRepository != null) {
                OfertaVendedor of = ofertaRepository.findById(item.getOfertaId()).orElse(null);
                if (of != null && of.getVendedor() != null) {
                    vId = of.getVendedor().getId();
                }
            }
            if (vId != null) {
                vendedoresIds.add(vId);
            }
        }

        return calcularTarifaParaVendedores(vendedoresIds, ciudadEntrega);
    }

    /**
     * Calcula tarifas por cada vendedor de una colección de vendedores.
     */
    public ResultadoCalculoEnvio calcularTarifaParaVendedores(Collection<String> vendedoresIds, String ciudadEntrega) {
        Integer zonaDestino = obtenerZonaPorCiudad(ciudadEntrega).orElse(null);
        Map<String, Long> tarifasPorVendedor = new LinkedHashMap<>();
        Map<String, Integer> zonasOrigenPorVendedor = new LinkedHashMap<>();
        List<DesgloseEnvioVendedor> desglose = new ArrayList<>();
        long totalCentavos = 0L;

        if (vendedoresIds == null || vendedoresIds.isEmpty()) {
            long defecto = obtenerTarifaPorDefectoCentavos();
            return new ResultadoCalculoEnvio(defecto, Collections.emptyMap(), Collections.emptyMap(), zonaDestino, Collections.emptyList());
        }

        String ciudadDestinoStr = ciudadEntrega;

        for (String vId : vendedoresIds) {
            Integer zonaOrigen = null;
            String ciudadOrigen = null;
            String nombreVendedor = vId;
            boolean tarifaPorDefecto = false;

            if (vId != null) {
                Optional<Usuario> optVendedor = usuarioRepository.findById(vId);
                if (optVendedor.isPresent()) {
                    Usuario v = optVendedor.get();
                    nombreVendedor = v.getNombre() != null ? v.getNombre() : vId;
                    if (v.getUbicacion() != null) {
                        ciudadOrigen = v.getUbicacion().getCiudad();
                        zonaOrigen = obtenerZonaPorCiudad(ciudadOrigen).orElse(null);
                    }
                }
            }

            // Tarifa por defecto si falta ciudad origen o zona destino
            if (zonaOrigen == null || zonaDestino == null) {
                tarifaPorDefecto = true;
            }

            long tarifa = calcularTarifaEntreZonas(zonaOrigen, zonaDestino);
            tarifasPorVendedor.put(vId, tarifa);
            if (zonaOrigen != null) {
                zonasOrigenPorVendedor.put(vId, zonaOrigen);
            }
            totalCentavos += tarifa;

            desglose.add(new DesgloseEnvioVendedor(
                    vId,
                    nombreVendedor,
                    ciudadOrigen,
                    zonaOrigen,
                    ciudadDestinoStr,
                    zonaDestino,
                    tarifa,
                    tarifaPorDefecto
            ));
        }

        return new ResultadoCalculoEnvio(totalCentavos, tarifasPorVendedor, zonasOrigenPorVendedor, zonaDestino, desglose);
    }
}
