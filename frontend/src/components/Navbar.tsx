import { useAuth } from '../context/AuthContext';

const SYSTEM_ROLES = new Set(['default-roles-hexagonal-scim', 'offline_access', 'uma_authorization']);

export default function Navbar() {
  const { username, roles, logout } = useAuth();

  const displayRoles = roles.filter((r) => !SYSTEM_ROLES.has(r));

  return (
    <nav className="bg-gray-900 border-b border-gray-800 px-6 py-3 flex items-center justify-between shrink-0">
      {/* Brand */}
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center shadow-lg">
          <span className="text-white font-bold text-sm select-none">H</span>
        </div>
        <span className="text-white font-semibold text-lg tracking-tight">HexSCIM</span>
      </div>

      {/* User info + logout */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <span className="text-gray-400 text-sm">
            @<span className="text-gray-200">{username}</span>
          </span>
          {displayRoles.map((role) => (
            <span
              key={role}
              className="px-2 py-0.5 text-xs font-medium rounded-full bg-indigo-950 text-indigo-300 border border-indigo-800"
            >
              {role}
            </span>
          ))}
        </div>

        <button
          onClick={logout}
          className="ml-2 text-sm text-gray-400 hover:text-white border border-gray-700 hover:border-gray-500 px-3 py-1.5 rounded-lg transition-colors"
        >
          Sign out
        </button>
      </div>
    </nav>
  );
}

