import csv
from datetime import datetime
from getpass import getpass

import requests


# ConfiguraciÃ³n
BASE_URL = "http://localhost:8080/flowable-ui/process-api"

# ID del elemento <process id="..."> del BPMN.
PROCESS_KEY = "process"

# Opcional: copiar aquÃ­ los IDs BPMN de las actividades y eventos
# de negociaciÃ³n que se quieran analizar.
# VacÃ­o = exportar todos los elementos histÃ³ricos finalizados.
ACTIVIDADES = set()
# Ejemplo ilustrativo:
# ACTIVIDADES = {"enviarPropuesta", "revisarPropuesta", "recordatorio"}

ARCHIVO_SALIDA = "eventos.csv"


def consultar_todos(session, recurso, filtros):
    """Recupera todas las pÃ¡ginas de una consulta REST."""
    inicio = 0

    while True:
        respuesta = session.get(
            f"{BASE_URL}/{recurso}",
            params={
                **filtros,
                "start": inicio,
                "size": 100,
            },
            timeout=30,
        )
        respuesta.raise_for_status()
        pagina = respuesta.json()
        registros = pagina["data"]

        if not registros:
            break

        yield from registros
        inicio += len(registros)

        if inicio >= pagina["total"]:
            break


def consultar_resultado(session, instancia_id):
    """Obtiene la variable 'resultado' de una instancia histórica."""
    valor = ""
    for variable in consultar_todos(
        session,
        "history/historic-variable-instances",
        {
            "processInstanceId": instancia_id,
            "variableName": "resultado",
        },
    ):
        dato = variable.get("variable") or variable
        valor_variable = dato.get("value") if isinstance(dato, dict) else None
        if valor_variable is not None:
            valor = str(valor_variable)
    return valor


def motivo_fin_actividad(session, actividad):
    tarea_id = actividad.get("taskId")
    if not tarea_id:
        return ""

    try:
        respuesta = session.get(
            f"{BASE_URL}/history/historic-task-instances/{tarea_id}",
            timeout=30,
        )
        respuesta.raise_for_status()
        motivo = respuesta.json().get("deleteReason")
    except (requests.RequestException, ValueError):
        # No vacío: analizar.py excluye la tarea en vez de contarla como completada.
        return "ERROR"

    return str(motivo) if motivo is not None else ""


def fecha_iso(valor):
    return datetime.fromisoformat(valor.replace("Z", "+00:00"))


def main():
    usuario = input("Usuario de Flowable: ")
    clave = getpass("ContraseÃ±a: ")

    eventos = []

    with requests.Session() as session:
        session.auth = (usuario, clave)

        instancias = consultar_todos(
            session,
            "history/historic-process-instances",
            {
                "processDefinitionKey": PROCESS_KEY,
                "finished": "true",
            },
        )

        for instancia in instancias:
            # Excluir instancias eliminadas administrativamente.
            if instancia.get("deleteReason"):
                continue

            resultado = consultar_resultado(session, instancia["id"])

            actividades = consultar_todos(
                session,
                "history/historic-activity-instances",
                {"processInstanceId": instancia["id"]},
            )

            for actividad in actividades:
                # No exportar elementos sin fecha de finalizaciÃ³n.
                if not actividad.get("endTime"):
                    continue

                if (
                    ACTIVIDADES
                    and actividad["activityId"] not in ACTIVIDADES
                ):
                    continue

                motivo_fin = motivo_fin_actividad(session, actividad)
                eventos.append({
                    "instancia": instancia["id"],
                    "resultado": resultado,
                    "actividad_fin": (
                        instancia.get("endActivityId") or ""
                    ),
                    "definicion": actividad["processDefinitionId"],
                    "evento_id": actividad["id"],
                    "actividad_id": actividad["activityId"],
                    "actividad": (
                        actividad.get("activityName")
                        or actividad["activityId"]
                    ),
                    "tipo": actividad["activityType"],
                    "inicio": actividad["startTime"],
                    "fecha_hora": actividad["endTime"],
                    "recurso": actividad.get("assignee") or "",
                    "motivo_fin": motivo_fin,
                })

    # Ordenar por instancia y fecha de finalizaciÃ³n.
    # El ID da un orden estable si coinciden las marcas temporales.
    eventos.sort(
        key=lambda evento: (
            evento["instancia"],
            fecha_iso(evento["fecha_hora"]),
            fecha_iso(evento["inicio"]),
            evento["evento_id"],
        )
    )

    columnas = [
        "instancia", "definicion", "evento_id",
        "actividad_id", "actividad", "tipo",
        "inicio", "fecha_hora", "recurso",
        "actividad_fin", "resultado", "motivo_fin",
    ]

    with open(
        ARCHIVO_SALIDA, "w", newline="", encoding="utf-8"
    ) as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=columnas)
        escritor.writeheader()
        escritor.writerows(eventos)

    cantidad_instancias = len({
        evento["instancia"] for evento in eventos
    })

    print(
        f"Generado {ARCHIVO_SALIDA}: "
        f"{len(eventos)} eventos de {cantidad_instancias} instancias."
    )


if __name__ == "__main__":
    main()