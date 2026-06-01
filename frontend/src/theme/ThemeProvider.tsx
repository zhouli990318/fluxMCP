import { createTheme, ThemeProvider as MuiThemeProvider, CssBaseline } from '@mui/material';
import { ReactNode, useMemo } from 'react';

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode: 'light',
          primary: { main: '#1976d2' },
          secondary: { main: '#9c27b0' },
          background: { default: '#fafafa', paper: '#ffffff' },
        },
        shape: { borderRadius: 8 },
        typography: {
          fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
          button: { textTransform: 'none', fontWeight: 500 },
        },
        components: {
          MuiCssBaseline: {
            styleOverrides: {
              body: { WebkitFontSmoothing: 'antialiased', MozOsxFontSmoothing: 'grayscale' },
            },
          },
          MuiButton: {
            styleOverrides: { root: { borderRadius: 6, boxShadow: 'none', '&:hover': { boxShadow: 'none' } } },
          },
          MuiCard: {
            styleOverrides: { root: { borderRadius: 8 } },
          },
          MuiDialog: {
            styleOverrides: { paper: { borderRadius: 12 } },
          },
        },
      }),
    [],
  );

  return (
    <MuiThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </MuiThemeProvider>
  );
}
