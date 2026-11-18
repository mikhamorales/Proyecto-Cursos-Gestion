<#
.SYNOPSIS
    Pruebas de escenarios del BPMN "Institucion de capacitacion" contra Flowable real.

.DESCRIPTION
    Ejecuta 5 escenarios de extremo a extremo usando la API REST de Flowable y la BD
    "institucion" de PostgreSQL del docker-compose:

      1. Curso aprobado (mensaje Aprobacion).
      2. Curso rechazado (mensaje Rechazo).
      3. Curso modificado (mensaje Modificacion y reinicio de recordatorios).
      4. Umbral de viabilidad del DMN (4 cursos en preparacion -> viable; 5 -> no viable).
      5. Empresa que ya recibio 2 recordatorios -> cierre por falta de respuesta.

    Requisitos: flowable-ui (8080) y flowable-postgres corriendo, con la definicion
    "process" desplegada y el JAR de listeners montado en el contenedor.

    Las instancias y las filas "curso" creadas por el script se borran al final.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/test-escenarios-flowable.ps1
#>

param(
    [string]$BaseUrl    = 'http://localhost:8080/flowable-ui/process-api',
    [string]$RestUser   = 'admin',
    [string]$RestPass   = 'test',
    [string]$DbContainer = 'flowable-postgres',
    [string]$DbName     = 'institucion',
    [string]$DbUser     = 'flowable',
    [string]$DbPass     = 'flow123'
)

$ErrorActionPreference = 'Stop'
$script:RunId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$script:Instances = New-Object System.Collections.Generic.List[string]
$script:CursoKeys = New-Object System.Collections.Generic.List[string]
$script:Failures = New-Object System.Collections.Generic.List[string]
$cred = New-Object PSCredential($RestUser, (ConvertTo-SecureString $RestPass -AsPlainText -Force))

# -----------------------------------------------------------------------------
# Helpers HTTP
# -----------------------------------------------------------------------------
function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        $Body
    )

    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Credential      = $cred
        UseBasicParsing = $true
        ErrorAction     = 'Stop'
    }
    if ($null -ne $Body) {
        $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
        $params.ContentType = 'application/json'
    }

    try {
        $r = Invoke-WebRequest @params
        $data = $null
        if ($r.Content) { $data = $r.Content | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Data = $data }
    }
    catch {
        $resp = $_.Exception.Response
        $code = 0
        $text = $_.Exception.Message
        if ($resp) {
            $code = [int]$resp.StatusCode
            $reader = New-Object IO.StreamReader($resp.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        return [pscustomobject]@{ Status = $code; Data = $text }
    }
}

function Start-Instance {
    param([Parameter(Mandatory = $true)][string]$Empresa)
    $body = @{
        messageName         = 'Solicitud de curso'
        processDefinitionKey = 'process'
        variables = @(
            @{ name = 'empresa';  value = $Empresa; type = 'string' },
            @{ name = 'tematica'; value = "E2E $script:RunId"; type = 'string' }
        )
    }
    $r = Invoke-Api POST '/runtime/process-instances' $body
    if ($r.Status -ne 201) { throw "No se pudo iniciar la instancia: HTTP $($r.Status) $($r.Data)" }
    $script:Instances.Add($r.Data.id)
    return $r.Data.id
}

# Devuelve una variable de una instancia viva; si ya termino, la busca en el historial.
function Get-VariableValue {
    param(
        [Parameter(Mandatory = $true)][string]$ProcessInstanceId,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $r = Invoke-Api GET "/runtime/process-instances/$ProcessInstanceId/variables"
    if ($r.Status -eq 200) {
        $v = $r.Data | Where-Object { $_.name -eq $Name } | Select-Object -First 1
        if ($v) { return $v.value }
    }
    $h = Invoke-Api GET "/history/historic-variable-instances?processInstanceId=$ProcessInstanceId"
    if ($h.Status -eq 200) {
        $v = $h.Data.data | Where-Object { $_.variable.name -eq $Name } | Select-Object -First 1
        if ($v) { return $v.variable.value }
    }
    return $null
}

function Get-Task {
    param(
        [Parameter(Mandatory = $true)][string]$ProcessInstanceId,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $r = Invoke-Api GET "/runtime/tasks?processInstanceId=$ProcessInstanceId"
    if ($r.Status -ne 200) { return $null }
    return $r.Data.data | Where-Object { $_.name -eq $Name } | Select-Object -First 1
}

function Complete-Task {
    param(
        [Parameter(Mandatory = $true)][string]$TaskId,
        $Variables = @()
    )
    return Invoke-Api POST "/runtime/tasks/$TaskId" @{ action = 'complete'; variables = $Variables }
}

function Send-Message {
    param(
        [Parameter(Mandatory = $true)][string]$ProcessInstanceId,
        [Parameter(Mandatory = $true)][string]$MessageName
    )
    $subs = Invoke-Api GET "/runtime/event-subscriptions?processInstanceId=$ProcessInstanceId&eventType=message"
    $sub = $subs.Data.data | Where-Object { $_.eventName -eq $MessageName } | Select-Object -First 1
    if (-not $sub) { throw "No hay suscripcion '$MessageName' en la instancia $ProcessInstanceId" }
    return Invoke-Api PUT "/runtime/executions/$($sub.executionId)" @{
        action      = 'messageEventReceived'
        messageName = $MessageName
        variables   = @()
    }
}

function Fire-Next-Timer {
    param([Parameter(Mandatory = $true)][string]$ProcessInstanceId)
    $timers = Invoke-Api GET "/management/timer-jobs?processInstanceId=$ProcessInstanceId"
    if ($timers.Status -ne 200 -or $timers.Data.data.Count -eq 0) { return $false }
    $timerId = $timers.Data.data[0].id

    $move = Invoke-Api POST "/management/timer-jobs/$timerId" @{ action = 'move' }
    if ($move.Status -ne 204) { throw "No se pudo mover el timer: HTTP $($move.Status)" }

    $jobs = Invoke-Api GET "/management/jobs?processInstanceId=$ProcessInstanceId"
    if ($jobs.Status -ne 200 -or $jobs.Data.data.Count -eq 0) {
        # El async executor pudo tomarlo apenas se movio; lo valida el assertion siguiente.
        return $true
    }
    $exec = Invoke-Api POST "/management/jobs/$($jobs.Data.data[0].id)" @{ action = 'execute' }
    if ($exec.Status -eq 404) {
        # Ya fue ejecutado por el async executor.
        return $true
    }
    if ($exec.Status -ne 204) { throw "No se pudo ejecutar el job: HTTP $($exec.Status)" }
    return $true
}

# -----------------------------------------------------------------------------
# Helpers de base de datos
# -----------------------------------------------------------------------------
function Invoke-Psql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    return (& docker exec $DbContainer psql -U $DbUser -d $DbName -tAc $Sql 2>&1)
}

function Get-CursosEnPreparacion {
    return [int](Invoke-Psql "select count(*) from curso where estado = 'EN_PREPARACION'").ToString().Trim()
}

function Add-CursosEnPreparacion {
    param([Parameter(Mandatory = $true)][int]$Cantidad)
    $primero = $script:CursoKeys.Count + 1
    for ($i = $primero; $i -lt ($primero + $Cantidad); $i++) {
        $cursoKey = "E2E-$script:RunId-$i"
        $null = Invoke-Psql "insert into curso (empresa_id, process_instance_id, nombre, estado) values (1, '$cursoKey', 'E2E $i', 'EN_PREPARACION')"
        $script:CursoKeys.Add($cursoKey)
    }
}

function Clear-CursosDePrueba {
    foreach ($cursoKey in $script:CursoKeys) {
        $null = Invoke-Psql "delete from curso where process_instance_id = '$cursoKey'"
    }
    $script:CursoKeys.Clear()
}

# -----------------------------------------------------------------------------
# Asserts
# -----------------------------------------------------------------------------
function Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "  [OK]   $Name" -ForegroundColor Green
    }
    else {
        Write-Host "  [FAIL] $Name $Detail" -ForegroundColor Red
        $script:Failures.Add("$Name $Detail")
    }
}

# -----------------------------------------------------------------------------
# Escenarios
# -----------------------------------------------------------------------------
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "TESTS DE ESCENARIOS BPMN - run $script:RunId" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan

try {
    # --- 1. Aprobado ---------------------------------------------------------
    Write-Host "`n[1] Curso aprobado" -ForegroundColor Yellow
    $pi = Start-Instance 'E2E Aprobado'
    Check 'DMN marca viable con menos de 5 cursos' ((Get-VariableValue $pi 'viable') -eq $true)
    $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
    Check 'Aparece "Registrar y elaborar propuesta"' ($null -ne $tarea)
    if ($tarea) {
        $r = Complete-Task $tarea.id
        Check 'Se completa la propuesta' ($r.Status -eq 200)
        $r = Send-Message $pi 'Aprobacion'
        Check 'Se envia mensaje Aprobacion' ($r.Status -eq 204)
        Check 'Queda accesoHabilitado=true' ((Get-VariableValue $pi 'accesoHabilitado') -eq $true)
        Check 'Llega a "Preparar materiales"' ($null -ne (Get-Task $pi 'Preparar materiales'))
    }

    # --- 2. Rechazado --------------------------------------------------------
    Write-Host "`n[2] Curso rechazado" -ForegroundColor Yellow
    $pi = Start-Instance 'E2E Rechazado'
    $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
    if ($tarea) { $null = Complete-Task $tarea.id }
    $r = Send-Message $pi 'Rechazo'
    Check 'Se envia mensaje Rechazo' (@(200, 204) -contains $r.Status) "HTTP $($r.Status)"
    Check 'resultado=RECHAZADO' ((Get-VariableValue $pi 'resultado') -eq 'RECHAZADO')
    $archivar = Get-Task $pi 'Archivar solicitud'
    Check 'Aparece "Archivar solicitud"' ($null -ne $archivar)
    if ($archivar) {
        $r = Complete-Task $archivar.id
        Check 'Se archiva y termina la instancia' ($r.Status -eq 200)
        $gp = Invoke-Api GET "/runtime/process-instances/$pi"
        Check 'La instancia ya no esta activa' ($gp.Status -eq 404)
    }

    # --- 3. Modificado -------------------------------------------------------
    Write-Host "`n[3] Curso modificado" -ForegroundColor Yellow
    $pi = Start-Instance 'E2E Modificado'
    $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
    if ($tarea) { $null = Complete-Task $tarea.id }
    $r = Send-Message $pi 'Modificacion'
    Check 'Se envia mensaje Modificacion' (@(200, 204) -contains $r.Status) "HTTP $($r.Status)"
    $revisar = Get-Task $pi 'Revisar propuesta'
    Check 'Aparece "Revisar propuesta"' ($null -ne $revisar)
    if ($revisar) {
        $r = Complete-Task $revisar.id
        Check 'Se reenvia la propuesta' ($r.Status -eq 200)
        Check 'El contador de recordatorios vuelve a 0' ((Get-VariableValue $pi 'recordatorios') -eq 0)
        $subs = Invoke-Api GET "/runtime/event-subscriptions?processInstanceId=$pi&eventType=message"
        $tieneAprobacion = ($subs.Data.data | Where-Object { $_.eventName -eq 'Aprobacion' } | Measure-Object).Count -gt 0
        Check 'Vuelve a esperar Aprobacion' $tieneAprobacion
    }

    # --- 4. Umbral del DMN ---------------------------------------------------
    Write-Host "`n[4] Umbral de viabilidad (4 vs 5 cursos en preparacion)" -ForegroundColor Yellow
    $base = Get-CursosEnPreparacion
    if ($base -gt 4) {
        Check "La BD de prueba tiene $base cursos en preparacion (se esperaba <=4)" $false
    }
    else {
        if ($base -lt 4) { Add-CursosEnPreparacion (4 - $base) }
        Check 'Hay exactamente 4 cursos en preparacion' ((Get-CursosEnPreparacion) -eq 4)

        $pi = Start-Instance 'E2E Umbral 4'
        Check 'Con 4: cursosEnPreparacion=4' ((Get-VariableValue $pi 'cursosEnPreparacion') -eq 4)
        Check 'Con 4: viable=true' ((Get-VariableValue $pi 'viable') -eq $true)
        $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
        Check 'Con 4: sigue el flujo normal' ($null -ne $tarea)

        Add-CursosEnPreparacion 1
        Check 'Hay exactamente 5 cursos en preparacion' ((Get-CursosEnPreparacion) -eq 5)
        $pi = Start-Instance 'E2E Umbral 5'
        Check 'Con 5: cursosEnPreparacion=5' ((Get-VariableValue $pi 'cursosEnPreparacion') -eq 5)
        Check 'Con 5: viable=false (la regla es >=5)' ((Get-VariableValue $pi 'viable') -eq $false)
        Check 'Con 5: resultado=NO_VIABLE' ((Get-VariableValue $pi 'resultado') -eq 'NO_VIABLE')
        $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
        Check 'Con 5: no se crea la tarea de propuesta' ($null -eq $tarea)
    }
    Clear-CursosDePrueba

    # --- 5. Dos recordatorios ------------------------------------------------
    Write-Host "`n[5] Empresa con 2 notificaciones/recordatorios" -ForegroundColor Yellow
    $pi = Start-Instance 'E2E Recordatorios'
    $tarea = Get-Task $pi 'Registrar y elaborar propuesta'
    if ($tarea) { $null = Complete-Task $tarea.id }

    Check 'Primer timer pendiente' ((Invoke-Api GET "/management/timer-jobs?processInstanceId=$pi").Data.data.Count -eq 1)
    $null = Fire-Next-Timer $pi
    Check 'Tras el 1er timer: recordatorios=1' ((Get-VariableValue $pi 'recordatorios') -eq 1)
    $null = Fire-Next-Timer $pi
    Check 'Tras el 2do timer: recordatorios=2' ((Get-VariableValue $pi 'recordatorios') -eq 2)
    $tarea = Get-Task $pi 'Archivar solicitud'
    Check 'Con 2 recordatorios sigue esperando respuesta' ($null -eq $tarea)

    $null = Fire-Next-Timer $pi
    Check 'Tras el 3er timer: resultado=CERRADO_POR_FALTA_DE_RESPUESTA' `
        ((Get-VariableValue $pi 'resultado') -eq 'CERRADO_POR_FALTA_DE_RESPUESTA')
    $archivar = Get-Task $pi 'Archivar solicitud'
    Check 'Tras el 3er timer: aparece "Archivar solicitud"' ($null -ne $archivar)
}
finally {
    Write-Host "`nLimpiando instancias y filas de prueba..." -ForegroundColor DarkGray
    foreach ($id in $script:Instances) {
        try { $null = Invoke-Api DELETE "/runtime/process-instances/$id" } catch { }
    }
    Clear-CursosDePrueba
    $script:Instances.Clear()
}

Write-Host "`n====================================================================" -ForegroundColor Cyan
if ($script:Failures.Count -eq 0) {
    Write-Host "RESULTADO: TODOS LOS ESCENARIOS PASARON" -ForegroundColor Green
    exit 0
}
Write-Host "RESULTADO: $($script:Failures.Count) FALLO(S)" -ForegroundColor Red
$script:Failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
exit 1
