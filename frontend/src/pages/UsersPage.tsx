import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getUsers, createUser, updateUser, deleteUser } from '../api/users';
import type { User } from '../types/user';
import UserTable from '../components/UserTable';
import UserFormModal from '../components/UserFormModal';
import ConfirmDialog from '../components/ConfirmDialog';
import Pagination from '../components/Pagination';

const PAGE_SIZE = 10;

export default function UsersPage() {
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deletingUser, setDeletingUser] = useState<User | null>(null);

  const qc = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['users', page],
    queryFn: () => getUsers(page, PAGE_SIZE),
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setShowCreate(false);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { email: string; displayName: string } }) =>
      updateUser(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setEditingUser(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setDeletingUser(null);
      // Step back a page if we just deleted the last row on a non-first page
      if (data?.content.length === 1 && page > 0) {
        setPage((p) => p - 1);
      }
    },
  });

  return (
    <div className="flex-1 p-8 max-w-5xl mx-auto w-full">
      {/* Page header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Users</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {data !== undefined
              ? `${data.totalElements} ${data.totalElements === 1 ? 'user' : 'users'} total`
              : '\u00a0'}
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white text-sm font-medium rounded-xl shadow transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Create User
        </button>
      </div>

      {/* Table card */}
      <div className="bg-gray-900 rounded-2xl border border-gray-800 overflow-hidden shadow-xl">
        {isLoading && (
          <div className="flex justify-center items-center py-20">
            <div className="w-8 h-8 rounded-full border-4 border-indigo-600 border-t-transparent animate-spin" />
          </div>
        )}

        {isError && (
          <div className="text-center py-20">
            <p className="text-red-400 text-sm">Failed to load users.</p>
            <p className="text-gray-600 text-xs mt-1">Check backend connectivity and token validity.</p>
          </div>
        )}

        {data && (
          <>
            <UserTable
              users={data.content}
              onEdit={setEditingUser}
              onDelete={setDeletingUser}
            />

            {data.totalPages > 1 && (
              <Pagination
                page={data.pageNumber}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                pageSize={data.pageSize}
                hasNext={data.hasNext}
                hasPrevious={data.hasPrevious}
                onPageChange={setPage}
              />
            )}
          </>
        )}
      </div>

      {/* Create modal */}
      {showCreate && (
        <UserFormModal
          onClose={() => setShowCreate(false)}
          onSubmit={(d) => createMutation.mutateAsync(d)}
        />
      )}

      {/* Edit modal */}
      {editingUser && (
        <UserFormModal
          user={editingUser}
          onClose={() => setEditingUser(null)}
          onSubmit={(d) => updateMutation.mutateAsync({ id: editingUser.id, data: d })}
        />
      )}

      {/* Delete confirmation */}
      {deletingUser && (
        <ConfirmDialog
          title="Delete User"
          message={`Are you sure you want to delete "${deletingUser.displayName}" (${deletingUser.email})? This cannot be undone.`}
          onCancel={() => setDeletingUser(null)}
          onConfirm={() => deleteMutation.mutate(deletingUser.id)}
          loading={deleteMutation.isPending}
        />
      )}
    </div>
  );
}

