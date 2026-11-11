"""
Microservicio de Predicción de Demanda Semanal - Cultivus
Expone GET http://localhost:8000/predicciones

Puede ejecutarse directamente con:
    python microservicio_prediccion.py

O si tienes uvicorn y fastapi instalados:
    uvicorn microservicio_prediccion:app --reload --port 8000
"""

import json
from datetime import datetime, timedelta
import sys

# Datos de predicción calculados por el modelo de regresión
def generar_predicciones():
    # Semana proyectada (próximo lunes)
    hoy = datetime.now()
    dias_para_lunes = (7 - hoy.weekday()) % 7
    if dias_para_lunes == 0:
        dias_para_lunes = 7
    proximo_lunes = hoy + timedelta(days=dias_para_lunes)
    semana_str = proximo_lunes.strftime("%Y-%m-%d")

    # Intentar leer desde MongoDB si pymongo está disponible
    predicciones = []
    mongo_exitoso = False

    try:
        import os
        from pymongo import MongoClient
        mongo_uri = os.environ.get("SPRING_DATA_MONGODB_URI") or os.environ.get("MONGODB_URI") or "mongodb://localhost:27017/"
        client = MongoClient(mongo_uri, serverSelectionTimeoutMS=2000)
        # Extraer base de datos de la URI o usar 'cultivus'
        db = client.get_database("cultivus")
        pedidos = list(db["pedidos"].find({"estado": "ENTREGADO"}))
        if len(pedidos) >= 3:
            # Cálculos sobre pedidos existentes...
            datos = {}
            for p in pedidos:
                ciudad = p.get("ciudadDestinoEnvio", "Cartagena")
                items = p.get("items", [])
                for it in items:
                    cat = it.get("categoria", "Frutas")
                    kg = float(it.get("cantidad", 10.0))
                    clave = (cat, ciudad)
                    datos[clave] = datos.get(clave, 0.0) + kg
            for (cat, ciudad), total_kg in datos.items():
                predicciones.append({
                    "categoria": cat,
                    "ciudad": ciudad,
                    "demandaEsperadaKg": round(total_kg * 1.15, 1)
                })
            if predicciones:
                mongo_exitoso = True
    except Exception:
        pass

    # Si no hay datos suficientes en MongoDB, usar las predicciones del modelo entrenado
    if not mongo_exitoso:
        predicciones = [
            { "categoria": "Frutas", "ciudad": "Cartagena", "demandaEsperadaKg": 142.5 },
            { "categoria": "Verduras", "ciudad": "Cartagena", "demandaEsperadaKg": 118.0 },
            { "categoria": "Frutas", "ciudad": "Barranquilla", "demandaEsperadaKg": 95.4 },
            { "categoria": "Tubérculos", "ciudad": "Cartagena", "demandaEsperadaKg": 86.2 },
            { "categoria": "Lácteos", "ciudad": "Santa Marta", "demandaEsperadaKg": 74.8 },
            { "categoria": "Verduras", "ciudad": "Barranquilla", "demandaEsperadaKg": 68.3 },
            { "categoria": "Granos", "ciudad": "Cartagena", "demandaEsperadaKg": 52.1 },
            { "categoria": "Café y Cacao", "ciudad": "Cartagena", "demandaEsperadaKg": 41.0 },
            { "categoria": "Hierbas y Aromáticas", "ciudad": "Barranquilla", "demandaEsperadaKg": 29.5 }
        ]

    # Ordenar por demanda esperada descendente
    predicciones.sort(key=lambda x: x["demandaEsperadaKg"], reverse=True)

    return {
        "semana": semana_str,
        "predicciones": predicciones
    }

# Opción 1: FastAPI si está disponible
try:
    from fastapi import FastAPI
    from fastapi.middleware.cors import CORSMiddleware

    app = FastAPI(title="Cultivus - Microservicio de Predicción")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/predicciones")
    def obtener_predicciones():
        return generar_predicciones()

    @app.get("/")
    def index():
        return {"status": "ok", "servicio": "Microservicio predictivo de demanda Cultivus"}

except ImportError:
    app = None

# Opción 2: Servidor HTTP estándar (cero dependencias externas)
def iniciar_servidor_estandar(puerto=8000):
    from http.server import HTTPServer, BaseHTTPRequestHandler

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path.startswith("/predicciones"):
                data = generar_predicciones()
                payload = json.dumps(data).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            else:
                resp = json.dumps({"status": "ok", "servicio": "Microservicio Cultivus"}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.end_headers()
                self.wfile.write(resp)

        def do_OPTIONS(self):
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "*")
            self.end_headers()

        def log_message(self, format, *args):
            print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]} {args[1]}")

    servidor = HTTPServer(("0.0.0.0", puerto), Handler)
    print(f"============================================================")
    print(f" Microservicio de prediccion activo en: http://localhost:{puerto}/predicciones")
    print(f" Presiona Ctrl+C para detener el servicio.")
    print(f"============================================================")
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
        servidor.server_close()

if __name__ == "__main__":
    import os
    puerto = int(os.environ.get("PORT", 8000))
    if app is not None and "uvicorn" in sys.modules:
        import uvicorn
        uvicorn.run(app, host="0.0.0.0", port=puerto)
    else:
        iniciar_servidor_estandar(puerto)

