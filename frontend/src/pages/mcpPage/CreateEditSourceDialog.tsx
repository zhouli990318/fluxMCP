import { useEffect, useMemo, useState, memo } from 'react';
import {
  Box, Typography, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, Select, MenuItem, FormControl, InputLabel,
  Stack, Paper,
} from '@mui/material';
import type { McpApiSource } from '../../api/types';
import type { SourceFormState } from './types';
import { createEmptyAuthFields, validateAuthFields } from './utils';

export const CreateEditSourceDialog = memo(function CreateEditSourceDialog({
  open,
  initialSource,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  initialSource: McpApiSource | null;
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: SourceFormState) => void;
}) {
  const [form, setForm] = useState<SourceFormState>({
    name: '', description: '', baseUrl: '', authType: 'NONE',
    updateAuthConfig: false, authFields: createEmptyAuthFields(),
  });

  useEffect(() => {
    if (!open) return;
    if (initialSource) {
      setForm({
        name: initialSource.name, description: initialSource.description || '',
        baseUrl: initialSource.baseUrl || '', authType: initialSource.authType || 'NONE',
        updateAuthConfig: false, authFields: createEmptyAuthFields(),
      });
      return;
    }
    setForm({ name: '', description: '', baseUrl: '', authType: 'NONE', updateAuthConfig: false, authFields: createEmptyAuthFields() });
  }, [initialSource, open]);

  const shouldShowAuthFields = useMemo(() => {
    if (form.authType === 'NONE') return false;
    if (!initialSource) return true;
    return form.updateAuthConfig || form.authType !== initialSource.authType;
  }, [form.authType, form.updateAuthConfig, initialSource]);

  const authConfigError = useMemo(() => {
    if (!shouldShowAuthFields) return null;
    return validateAuthFields(form.authType, form.authFields);
  }, [form.authFields, form.authType, shouldShowAuthFields]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{initialSource ? '编辑 API 源' : '添加 API 源'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="名称" required value={form.name} onChange={(e) => setForm((c) => ({ ...c, name: e.target.value }))} />
        <TextField label="描述" value={form.description} onChange={(e) => setForm((c) => ({ ...c, description: e.target.value }))} />
        <TextField label="Base URL" value={form.baseUrl} onChange={(e) => setForm((c) => ({ ...c, baseUrl: e.target.value }))} />
        <FormControl>
          <InputLabel>认证方式</InputLabel>
          <Select
            value={form.authType}
            onChange={(e) => {
              const nextAuthType = String(e.target.value);
              setForm((c) => ({
                ...c, authType: nextAuthType,
                updateAuthConfig: initialSource ? nextAuthType !== 'NONE' && nextAuthType !== initialSource.authType : c.updateAuthConfig,
              }));
            }}
            label="认证方式"
          >
            <MenuItem value="NONE">无</MenuItem>
            <MenuItem value="API_KEY">API Key</MenuItem>
            <MenuItem value="BEARER_TOKEN">Bearer Token</MenuItem>
            <MenuItem value="BASIC_AUTH">Basic Auth</MenuItem>
          </Select>
        </FormControl>
        {initialSource && form.authType !== 'NONE' && form.authType === initialSource.authType && !form.updateAuthConfig && (
          <Paper variant="outlined" sx={{ p: 2, bgcolor: 'action.hover' }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Box>
                <Typography sx={{ fontWeight: 600, fontSize: 14 }}>当前认证配置已存在</Typography>
                <Typography variant="body2" color="text.secondary">为安全起见，不显示现有凭证。点击"更新认证配置"后可重新填写并覆盖。</Typography>
              </Box>
              <Button variant="outlined" onClick={() => setForm((c) => ({ ...c, updateAuthConfig: true }))}>更新认证配置</Button>
            </Stack>
          </Paper>
        )}
        {form.authType !== 'NONE' && shouldShowAuthFields && (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
              {initialSource && form.authType !== initialSource.authType && (
                <Typography variant="body2" color="text.secondary">认证方式已变更，请填写新的认证信息。</Typography>
              )}
              {form.authType === 'API_KEY' && (
                <>
                  <TextField label="API Key" required type="password" value={form.authFields.apiKey}
                    onChange={(e) => setForm((c) => ({ ...c, authFields: { ...c.authFields, apiKey: e.target.value } }))}
                    error={Boolean(authConfigError)} helperText={authConfigError || '用于请求头鉴权'} />
                  <TextField label="Header 名称" value={form.authFields.headerName}
                    onChange={(e) => setForm((c) => ({ ...c, authFields: { ...c.authFields, headerName: e.target.value } }))}
                    helperText="可选，默认使用后端默认值" placeholder="X-API-Key" />
                </>
              )}
              {form.authType === 'BEARER_TOKEN' && (
                <TextField label="Bearer Token" required type="password" value={form.authFields.token}
                  onChange={(e) => setForm((c) => ({ ...c, authFields: { ...c.authFields, token: e.target.value } }))}
                  error={Boolean(authConfigError)} helperText={authConfigError || '提交时会按 Bearer Token 方式序列化'} />
              )}
              {form.authType === 'BASIC_AUTH' && (
                <>
                  <TextField label="用户名" required value={form.authFields.username}
                    onChange={(e) => setForm((c) => ({ ...c, authFields: { ...c.authFields, username: e.target.value } }))}
                    error={Boolean(authConfigError)} />
                  <TextField label="密码" required type="password" value={form.authFields.password}
                    onChange={(e) => setForm((c) => ({ ...c, authFields: { ...c.authFields, password: e.target.value } }))}
                    error={Boolean(authConfigError)} helperText={authConfigError} />
                </>
              )}
            </Stack>
          </Paper>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={() => onSubmit(form)} disabled={loading || Boolean(authConfigError)}>
          {initialSource ? '保存' : '创建'}
        </Button>
      </DialogActions>
    </Dialog>
  );
});
