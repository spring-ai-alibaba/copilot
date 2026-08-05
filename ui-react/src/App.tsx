import { App as AntdApp, ConfigProvider } from "antd";
import AppShell from "./components/AppShell";
import "./utils/i18";

function App() {
  return (
    <ConfigProvider modal={{ style: { zIndex: 10100 } }}>
      <AntdApp>
        <AppShell />
      </AntdApp>
    </ConfigProvider>
  );
}

export default App;
