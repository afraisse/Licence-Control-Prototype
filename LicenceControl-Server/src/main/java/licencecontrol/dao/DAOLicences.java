package licencecontrol.dao;

import java.sql.*;

import licencecontrol.db.ConnectionMySql;

public class DAOLicences implements DAO {
	
	private static final String QUERY_VALIDATE_LIC = "SELECT COUNT(licence) as nb FROM `licences` WHERE `licence`= ?";
	private static final String QUERY_CHECKSUM = "SELECT `build_checksum` FROM builds b, licences l WHERE l.id_build = b.id_build AND l.licence = ?";
	private static final String QUERY_NB_MAX_USERS = "SELECT `nb_users_max` FROM `licences` WHERE `licence` = ?";
	private static final String QUERY_INSERT_TEMPORARY_KEY = "INSERT INTO `session` (`licence`, `session_key`, `expiration_date`) VALUES (?, ?, ?);";
	private static final String QUERY_NB_SESSIONS_ACTIVES = "SELECT COUNT(`session_key`) AS nb_sessions_actives FROM  `session` WHERE SYSDATE() <  `expiration_date` AND  `licence` = ?";
	private static final String QUERY_DELETE_SESSION = "DELETE FROM `session` WHERE `session_key`= ?";
	private static final String QUERY_SESSION_EXISTS = "SELECT `expiration_date` FROM  `session` WHERE  `licence` = ? AND `session_key` = ?";
	
	//private static DAOLicence instance;
	private Connection connection;

	public DAOLicences() {
		connection = ConnectionMySql.getConnection();
	}
	
	@Override
	public boolean validateLicence(String licence) throws DAOException {
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_VALIDATE_LIC);
			statement.setString(1, licence);
			ResultSet rs = statement.executeQuery();
			rs.first();
			int result = rs.getInt("nb");
			rs.close();
			return result > 0;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public String getChecksum(String licence) throws DAOException {
		//String sql = "SELECT `build_checksum` FROM builds b, licences l WHERE l.id_build = b.id_build AND l.licence = ?";
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_CHECKSUM);
			statement.setString(1, licence);
			ResultSet rs = statement.executeQuery();
			String checksum = null;
			if(rs.first()) {
				checksum = rs.getString("build_checksum");
			}
			rs.close();
			return checksum;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public int getNbMaxUsers(String licence) throws DAOException {
		//String sql = "SELECT `nb_users_max` FROM `licences` WHERE `licence` = ?";
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_NB_MAX_USERS);
			statement.setString(1, licence);
			ResultSet rs = statement.executeQuery();
			int nbUsersMax = 0;
			if(rs.first()) {
				nbUsersMax = rs.getInt("nb_users_max");
			}
			rs.close();
			return nbUsersMax;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public boolean insertTemporaryKey(String licence, String key, Timestamp timestamp)
			throws DAOException {
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_INSERT_TEMPORARY_KEY);
			statement.setString(1, licence);
			statement.setString(2, key);
			statement.setObject(3, timestamp);
			int rowAffected = statement.executeUpdate();
			return rowAffected == 1;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public int getNbActiveSessions(String licence) throws DAOException {
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_NB_SESSIONS_ACTIVES);
			statement.setString(1, licence);
			ResultSet rs = statement.executeQuery();
			int nbSessionsActives = 0;
			if(rs.first()) {
				nbSessionsActives = rs.getInt("nb_sessions_actives");
			}
			rs.close();
			return nbSessionsActives;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public boolean deleteSession(String sessionKey)
			throws DAOException {
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_DELETE_SESSION);
			statement.setString(1, sessionKey);
			int rowAffected = statement.executeUpdate();
			return rowAffected == 1;
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}

	@Override
	public SessionState sessionExists(String licence, String tempKey)
			throws DAOException {
		try {
			PreparedStatement statement = connection.prepareStatement(QUERY_SESSION_EXISTS);
			statement.setString(1, licence);
			statement.setString(2, tempKey);
			ResultSet rs = statement.executeQuery();
			if(rs.first()) {
				//expir�e ou non ?
				long expiration_date = rs.getTimestamp("expiration_date").getTime();
				long now = System.currentTimeMillis();
				//active
				if(expiration_date > now) {
					return SessionState.ACTIVE;
				}
				else {
					return SessionState.EXPIRED;
				}
			}
			else {
				return SessionState.NULL;
			}
		} catch (SQLException e) {
			throw new DAOException(e.getMessage());
		}
	}
}
