import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import Screen from "./Screen";

const rootElement = document.getElementById("content-integrity-root");
console.log("Initializing content-integrity UI", rootElement)
const root = createRoot(rootElement);
root.render(
    <StrictMode>
        <Screen />
    </StrictMode>
);
