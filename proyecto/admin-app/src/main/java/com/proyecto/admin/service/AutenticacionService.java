package com.proyecto.admin.service;

import java.util.List;

import org.flowable.engine.IdentityService;
import org.flowable.idm.api.Group;
import org.springframework.stereotype.Service;

/**
 * Valida credenciales contra el modulo IDM de Flowable y exige pertenencia al
 * grupo de administrativos.
 */
@Service
public class AutenticacionService {

	/** Unico grupo habilitado para operar las tareas de administracion. */
	public static final String GRUPO_ADMINISTRACION = "administracion";

	private final IdentityService identityService;

	public AutenticacionService(IdentityService identityService) {
		this.identityService = identityService;
	}

	/**
	 * Devuelve true solo si la contrasena es correcta y el usuario pertenece al
	 * grupo administracion. Un usuario valido de otro grupo (docentes, admin sin
	 * grupo) queda rechazado.
	 */
	public boolean esAdministrativo(String usuario, String contrasena) {
		if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty()) {
			return false;
		}
		if (!identityService.checkPassword(usuario, contrasena)) {
			return false;
		}
		return perteneceAlGrupo(usuario, GRUPO_ADMINISTRACION);
	}

	public boolean perteneceAlGrupo(String usuario, String grupoId) {
		return identityService.createGroupQuery()
				.groupMember(usuario)
				.groupId(grupoId)
				.count() > 0;
	}

	public List<String> gruposDe(String usuario) {
		return identityService.createGroupQuery()
				.groupMember(usuario)
				.list()
				.stream()
				.map(Group::getId)
				.toList();
	}

}
