import pandas as pd
import pm4py

# Leer el CSV. Mantener los identificadores como texto.
df = pd.read_csv("eventos.csv", dtype=str)

RESULTADOS = {
    "sid-392CF0AC-4B00-42BA-ABF2-4316FA1354D6": ("Curso finalizado",),
    "sid-20F1D2A6-3CB3-4249-8941-1F5AB50C6378": ("Cancelación",),
    "sid-A35BEC0D-8AF4-48B2-A423-0EB31BAD527E": (
        "Rechazo de la propuesta",
        "Cierre por falta de respuesta",
    ),
    "sid-3EBB93AC-2D16-40C1-88C9-BB8058BDD3E9": ("Solicitud no viable",),
}
RESULTADOS_POR_VARIABLE = {
    "FINALIZADO": "Curso finalizado",
    "CANCELADO": "Cancelación",
    "RECHAZADO": "Rechazo de la propuesta",
    "NO_VIABLE": "Solicitud no viable",
    "CERRADO_POR_FALTA_DE_RESPUESTA": "Cierre por falta de respuesta",
}
ETIQUETAS_RESULTADOS = [
    "Curso finalizado",
    "Cancelación",
    "Rechazo de la propuesta",
    "Solicitud no viable",
    "Cierre por falta de respuesta",
]
SIN_CLASIFICAR = "Sin clasificar"

actividades_fin = (
    df["actividad_fin"] if "actividad_fin" in df.columns
    else pd.Series("", index=df.index)
)
resultados_csv = (
    df["resultado"] if "resultado" in df.columns
    else pd.Series("", index=df.index)
)
instancias = pd.DataFrame({
    "instancia": df["instancia"],
    "resultado": resultados_csv.fillna(""),
    "actividad_fin": actividades_fin.fillna(""),
}).drop_duplicates("instancia")


def clasificar_resultado(resultado, actividad_fin):
    resultado = resultado.strip() if isinstance(resultado, str) else ""
    etiqueta = RESULTADOS_POR_VARIABLE.get(resultado)
    if etiqueta:
        return etiqueta
    etiquetas = RESULTADOS.get(actividad_fin, ())
    return etiquetas[0] if etiquetas else SIN_CLASIFICAR


instancias["clasificacion"] = [
    clasificar_resultado(resultado, actividad_fin)
    for resultado, actividad_fin in zip(
        instancias["resultado"], instancias["actividad_fin"]
    )
]

cantidades = {
    etiqueta: 0
    for etiqueta in ETIQUETAS_RESULTADOS + [SIN_CLASIFICAR]
}
for etiqueta in instancias["clasificacion"]:
    cantidades[etiqueta] += 1

total_instancias = len(instancias)
etiquetas_tabla = list(cantidades)
if total_instancias:
    exactos = [
        1000 * cantidades[etiqueta] / total_instancias
        for etiqueta in etiquetas_tabla
    ]
    decimos = [int(valor) for valor in exactos]
    restantes = 1000 - sum(decimos)
    for indice in sorted(
        range(len(decimos)),
        key=lambda indice: exactos[indice] - decimos[indice],
        reverse=True,
    )[:restantes]:
        decimos[indice] += 1
else:
    decimos = [0] * len(etiquetas_tabla)

print("\nResultados de las solicitudes:")
print(f"{'Resultado':<30} {'Cantidad':>9} {'Porcentaje':>12}")
for etiqueta, decimo in zip(etiquetas_tabla, decimos):
    porcentaje = f"{decimo / 10:.1f}".replace(".", ",")
    print(f"{etiqueta:<30} {cantidades[etiqueta]:>9} {porcentaje:>11} %")
porcentaje_total = "100,0" if total_instancias else "0,0"
print(f"{'Total':<30} {total_instancias:>9} {porcentaje_total:>11} %")

# Conservar solamente tareas de usuario y automÃ¡ticas.
RAMAS = {
    "Inscripciones": [
        "sid-982375C3-4EF8-435A-A291-BF9A1933A05C",
        "sid-6443AE13-2ACF-4CC6-AE8A-F6ADACC2DFC4",
    ],
    "Materiales": [
        "sid-E0F8C31B-8D21-4CBC-A2A7-C7213EA7FBFB",
    ],
    "Recursos": [
        "sid-FC42B03B-500A-4344-A46D-C18448C77F0D",
        "reservarAula",
        "aulaVirtual",
        "equipamiento",
    ],
}
FIN_PREPARACION = "sid-CF1955EC-B385-48C5-8E83-88EA58EEC24E"


def formatear_duracion(segundos):
    minutos = int(segundos // 60)
    dias, resto = divmod(minutos, 1440)
    horas, minutos = divmod(resto, 60)
    return f"{dias} d {horas} h {minutos} min"


# Duración de cada rama sobre las preparaciones que llegaron al elemento de unión.
instancias_preparadas = df.loc[
    df["actividad_id"] == FIN_PREPARACION, "instancia"
].dropna().unique()
print(f"\nInstancias con preparación completa: {len(instancias_preparadas)}")

if not len(instancias_preparadas):
    print("No hay instancias con preparación completa para medir las ramas.")
else:
    eventos_preparacion = df[
        df["instancia"].isin(instancias_preparadas)
    ].copy()
    eventos_preparacion["inicio_dt"] = pd.to_datetime(
        eventos_preparacion["inicio"], utc=True, errors="coerce"
    )
    eventos_preparacion["fin_dt"] = pd.to_datetime(
        eventos_preparacion["fecha_hora"], utc=True, errors="coerce"
    )

    print(f"\n{'Rama':<30} {'Casos':>6} {'Duración promedio':>20}")
    for rama, ids in RAMAS.items():
        resumen = (
            eventos_preparacion[eventos_preparacion["actividad_id"].isin(ids)]
            .groupby("instancia")
            .agg(
                activacion=("inicio_dt", "min"),
                finalizacion=("fin_dt", "max"),
            )
            .dropna()
        )
        duraciones = (
            resumen["finalizacion"] - resumen["activacion"]
        ).dt.total_seconds()
        cantidad = len(duraciones)
        promedio = (
            formatear_duracion(duraciones.mean()) if cantidad else "-"
        )
        print(f"{rama:<30} {cantidad:>6} {promedio:>20}")

ROLES = {
    "jefeAdmin": "Administrativo",
    "admin1": "Administrativo",
    "admin2": "Administrativo",
    "jefeDocente": "Docente",
    "docente1": "Docente",
    "docente2": "Docente",
}
SIN_ROL = "Sin rol definido"
SIN_ASIGNAR = "Sin asignar"
ORDEN_ROLES = ["Administrativo", "Docente", SIN_ROL, SIN_ASIGNAR]
TOTALES_ROL = {
    "Administrativo": "Total administrativos",
    "Docente": "Total docentes",
    SIN_ROL: "Total sin rol definido",
}
VACIOS = {"", "nan", "none"}


def es_vacio(valor):
    return valor is None or str(valor).strip().lower() in VACIOS


def usuario_y_rol(recurso):
    usuario = "" if es_vacio(recurso) else str(recurso).strip()
    if not usuario:
        return "-", SIN_ASIGNAR
    return usuario, ROLES.get(usuario, SIN_ROL)


def contar_tareas(recursos):
    conteos = {}
    for recurso in recursos:
        clave = usuario_y_rol(recurso)
        conteos[clave] = conteos.get(clave, 0) + 1
    return conteos


tareas_roles = df.copy()
fin_dt = pd.to_datetime(
    tareas_roles["fecha_hora"], utc=True, errors="coerce"
)
motivos = (
    tareas_roles["motivo_fin"]
    if "motivo_fin" in tareas_roles.columns
    else pd.Series("", index=tareas_roles.index)
)
completas = (
    tareas_roles["tipo"].eq("userTask")
    & fin_dt.notna()
    & motivos.map(es_vacio)
)
recursos = (
    tareas_roles.loc[completas, "recurso"]
    if "recurso" in tareas_roles.columns
    else []
)
conteos = contar_tareas(recursos)

print("\nDistribución de tareas de usuario completadas:")
print(f"{'Rol':<30} {'Usuario':<20} {'Tareas completadas':>18}")

if not conteos:
    print("No hay tareas de usuario completadas para mostrar.")
else:
    for rol in ORDEN_ROLES:
        filas = sorted(
            (
                (nombre, cantidad)
                for (nombre, rol_usuario), cantidad in conteos.items()
                if rol_usuario == rol
            ),
            key=lambda fila: (-fila[1], fila[0]),
        )
        for usuario, cantidad in filas:
            print(f"{rol:<30} {usuario:<20} {cantidad:>18}")
        if filas and rol in TOTALES_ROL:
            total = sum(cantidad for _, cantidad in filas)
            print(f"{TOTALES_ROL[rol]:<30} {'':<20} {total:>18}")

df = df[df["tipo"].isin(["userTask", "serviceTask"])].copy()


# Convertir fechas y conservar el orden del CSV ante empates.
df["fecha_hora"] = pd.to_datetime(df["fecha_hora"], utc=True)
df = df.sort_values(
    ["instancia", "fecha_hora"], kind="stable"
)

# Adaptar las columnas al formato utilizado por PM4Py.
log = pm4py.format_dataframe(
    df,
    case_id="instancia",
    activity_key="actividad",
    timestamp_key="fecha_hora",
)

# 1. Recorridos o variantes mÃ¡s frecuentes.
variantes = pm4py.get_variants_as_tuples(log)
total = log["case:concept:name"].nunique()

print(f"\nInstancias analizadas: {total}")
print(f"Recorridos diferentes: {len(variantes)}")

print("\nLos tres recorridos mÃ¡s frecuentes:")
for recorrido, cantidad in sorted(
    variantes.items(),
    key=lambda elemento: elemento[1],
    reverse=True,
)[:3]:
    print(f"\n{cantidad} casos ({100 * cantidad / total:.1f} %)")
    print(" â†’ ".join(recorrido))

# 2. Grafo de sucesiÃ³n directa:
# cada flecha indica cuÃ¡ntas veces una tarea siguiÃ³ a otra.
dfg, inicios, finales = pm4py.discover_dfg(log)
pm4py.save_vis_dfg(
    dfg, inicios, finales, "recorridos.png"
)

# 3. Modelo BPMN descubierto a partir de las ejecuciones.
modelo = pm4py.discover_bpmn_inductive(log)
pm4py.save_vis_bpmn(modelo, "modelo_descubierto.png")
pm4py.write_bpmn(modelo, "modelo_descubierto.bpmn")

print("\nArchivos generados:")
print("- recorridos.png")
print("- modelo_descubierto.png")
print("- modelo_descubierto.bpmn")