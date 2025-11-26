import {Dispatch, SetStateAction, useEffect, useRef, useState} from "react";
import {getContainerInstance} from "./WeIde/services";
import {ChevronDown, Laptop, Monitor, Smartphone, Tablet} from "lucide-react";
import {findWeChatDevToolsPath} from "./EditorPreviewTabs";
import {useFileStore} from "./WeIde/stores/fileStore";
import {useTranslation} from "react-i18next";

interface PreviewIframeProps {
  setShowIframe: Dispatch<SetStateAction<string>>;
  isMinPrograme: boolean;
}
interface WindowSize {
  name: string;
  width: number | string;
  height: number | string;
  icon: React.ComponentType<{ size?: string | number }>;
}
const WINDOW_SIZES: WindowSize[] = [
  { name: "Desktop", width: '100%', height:'100%', icon: Monitor },
  { name: "Mobile", width: 375, height: 667, icon: Smartphone },
  {
    name: "Tablet",
    width: Number((768 / 1.5).toFixed(0)),
    height: Number((1024 / 1.5).toFixed(0)),
    icon: Tablet,
  },
  { name: "Laptop", width: 1366, height: 768, icon: Laptop },
];

const PreviewIframe: React.FC<PreviewIframeProps> = ({
  setShowIframe,
  isMinPrograme,
}) => {
  const [url, setUrl] = useState<string>("");
  const [port, setPort] = useState<string>("");
  const { projectRoot } = useFileStore();
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [scale, setScale] = useState<number>(1);
  const { t } = useTranslation();
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedSize, setSelectedSize] = useState<WindowSize>(WINDOW_SIZES[0]);
  const [isWindowSizeDropdownOpen, setIsWindowSizeDropdownOpen] = useState(false);
  const [iframeLoaded, setIframeLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const instance = await getContainerInstance();
        if (instance) {
          instance.on("server-ready", (port, url) => {
            console.log("server-ready", port, url);
            setUrl(url);
            setShowIframe("preview");
            setPort(port.toString());
          });
        } else {
          console.warn("Container instance not available");
        }
      } catch (error) {
        console.error("Failed to get container instance:", error);
      }
    })();

  }, []);

  const handleRefresh = () => {
    console.log("刷新 handleRefresh", iframeRef.current);
    if (iframeRef.current) {
      iframeRef.current.src = iframeRef.current.src;
    }
  };

    const displayUrl = port
    ? `http://localhost:${port}`
    : isMinPrograme
      ? t('preview.wxminiPreview')
      : t('preview.noserver');

  const handleWheel = (e: WheelEvent) => {
    if (e.ctrlKey) {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setScale((prevScale) => {
        const newScale = prevScale * delta;
        return Math.min(Math.max(newScale, 0.5), 3);
      });
    }
  };

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let initialDistance = 0;
    let initialScale = 1;

    const handleTouchStart = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        e.preventDefault();
        initialDistance = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        initialScale = scale;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (e.touches.length === 2) {
        e.preventDefault();
        const distance = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        const delta = distance / initialDistance;
        const newScale = Math.min(Math.max(initialScale * delta, 0.5), 3);
        setScale(newScale);
      }
    };

    container.addEventListener("wheel", handleWheel, { passive: false });
    container.addEventListener("touchstart", handleTouchStart);
    container.addEventListener("touchmove", handleTouchMove);

    return () => {
      container.removeEventListener("wheel", handleWheel);
      container.removeEventListener("touchstart", handleTouchStart);
      container.removeEventListener("touchmove", handleTouchMove);
    };
  }, [scale]);

  const handleZoomIn = () => {
    setScale((prevScale) => Math.min(prevScale * 1.1, 3));
  };

  const handleZoomOut = () => {
    setScale((prevScale) => Math.max(prevScale * 0.9, 0.5));
  };

  const handleZoomReset = () => {
    setScale(1);
  };

  const openExternal = () => {
    const externalUrl = port ? `http://localhost:${port}/` : "http://localhost:5174/";
    window.electron.ipcRenderer.send(
      "open:external:url",
      externalUrl
    );
  };

  useEffect(() => {
    if (iframeLoaded && iframeRef.current?.contentWindow) {
      try {
        // Check if we can access the iframe's document (same-origin)
        const iframeWindow = iframeRef.current.contentWindow;

        // Test access to document - this will throw if cross-origin
        const testAccess = iframeWindow.document;

        const injectScript = `

        `;

        const script = iframeWindow.document.createElement('script');
        script.textContent = injectScript;
        iframeWindow.document.head.appendChild(script);

      } catch (error) {
        // Silently handle cross-origin access errors
        // This is expected when the iframe content is from a different origin
        if (error instanceof DOMException && error.name === 'SecurityError') {
          console.warn('Cannot inject script into iframe due to cross-origin restrictions');
        } else {
          console.error('Unexpected error accessing iframe:', error);
        }
      }
    }
  }, [iframeLoaded]);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.data.type === 'REQUEST_BLOB_ACCESS') {
        const blobUrl = event.data.blobUrl;
        const requestId = event.data.requestId;


        fetch(blobUrl)
          .then(response => {
            if (!response.ok) {
              throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.blob();
          })
          .then(blob => {
            const reader = new FileReader();
            reader.onloadend = function() {
              const base64data = reader.result as string;
              const base64Content = base64data.split(',')[1];
              if (iframeRef.current?.contentWindow) {
                try {
                  iframeRef.current.contentWindow.postMessage({
                    type: 'BLOB_ACCESS_GRANTED',
                    blobData: base64Content,
                    contentType: blob.type,
                    originalUrl: blobUrl,
                    requestId: requestId
                  }, '*');
                } catch (error) {
                  console.warn('Failed to send message to iframe:', error);
                }
              }
            };
            reader.onerror = function() {
              console.error('Failed to read blob as data URL');
            };
            reader.readAsDataURL(blob);
          })
          .catch(error => {
            console.error('Failed to fetch blob:', error);
            // Notify iframe about the error
            if (iframeRef.current?.contentWindow) {
              try {
                iframeRef.current.contentWindow.postMessage({
                  type: 'BLOB_ACCESS_ERROR',
                  error: error.message,
                  requestId: requestId
                }, '*');
              } catch (postError) {
                console.warn('Failed to send error message to iframe:', postError);
              }
            }
          });
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, []);

  return (
    <div     className="preview-container w-full h-full relative flex flex-col overflow-hidden">
      <div className="browser-header bg-white dark:bg-[#1a1a1c] border-b border-gray-200 px-4 py-1 flex items-center space-x-2">
        <div className="flex space-x-1.5">
          <div className="w-3 h-3 rounded-full bg-red-500"></div>
          <div className="w-3 h-3 rounded-full bg-yellow-500"></div>
          <div
            className="w-3 h-3 rounded-full bg-green-500"
            onClick={openExternal}
          ></div>
        </div>
        <div className="relative">
          <button
            className="ml-2 p-1.5 rounded hover:bg-gray-100 dark:hover:bg-[#2c2c2c] text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 flex items-center gap-2"
            onClick={() =>
              setIsWindowSizeDropdownOpen(!isWindowSizeDropdownOpen)
            }
          >
            <selectedSize.icon size={16} />
            <ChevronDown size={16} />
          </button>
          {isWindowSizeDropdownOpen && (
            <>
              <div
                className="fixed inset-0 z-50"
                onClick={() => setIsWindowSizeDropdownOpen(false)}
              />
              <div className="absolute top-8 left-0 mt-2 z-50 min-w-[240px] bg-white dark:bg-black rounded-xl shadow-2xl border border-[#E5E7EB] dark:border-[rgba(255,255,255,0.1)] overflow-hidden">
                {WINDOW_SIZES.map((size) => (
                  <button
                    key={size.name}
                    className="w-full px-4 py-3.5 text-left text-[#111827] dark:text-gray-300 text-sm whitespace-nowrap flex items-center gap-3 group hover:bg-[#F5EEFF] dark:hover:bg-gray-900 bg-white dark:bg-black"
                    onClick={async () => {
                      setSelectedSize(size);
                      setIsWindowSizeDropdownOpen(false);
                      // WeChat DevTools preview is not available in Web mode
                      if (isMinPrograme) {
                        console.warn("WeChat DevTools preview is not available in Web mode");
                      }
                    }}
                  >
                    <size.icon size={20} />
                    <div className="flex flex-col">
                      <span className="font-medium group-hover:text-[#6D28D9] dark:group-hover:text-[#6D28D9] transition-colors duration-200">
                        {size.name}
                      </span>
                      <span className="text-xs text-[#6B7280] dark:text-gray-400 group-hover:text-[#6D28D9] dark:group-hover:text-[#6D28D9] transition-colors duration-200">
                        {size.width} × {size.height}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
        <div className="flex-1 ml-4 flex items-center">
          <div className="px-3 py-1 rounded-md text-sm text-gray-800 dark:text-gray-50 border bg-gray-50 dark:bg-[#2c2c2c] border-gray-200 dark:border-black w-full truncate">
            {displayUrl}
          </div>
          <button
            onClick={handleRefresh}
            className="ml-2 p-1 rounded hover:bg-gray-100 dark:hover:bg-[#2c2c2c] text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-4 w-4"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z"
                clipRule="evenodd"
              />
            </svg>
          </button>
          <div className="ml-2 flex items-center space-x-1">
            <button
              onClick={handleZoomOut}
              className="p-1 rounded hover:bg-gray-100 dark:hover:bg-[#2c2c2c] text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200"
              title="缩小"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-4 w-4"
                viewBox="0 0 20 20"
                fill="currentColor"
              >
                <path
                  fillRule="evenodd"
                  d="M5 10a1 1 0 011-1h8a1 1 0 110 2H6a1 1 0 01-1-1z"
                  clipRule="evenodd"
                />
              </svg>
            </button>
            <button
              onClick={handleZoomReset}
              className="px-2 py-0.5 rounded hover:bg-gray-100 dark:hover:bg-[#2c2c2c] text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 text-xs"
            >
              {Math.round(scale * 100)}%
            </button>
            <button
              onClick={handleZoomIn}
              className="p-1 rounded hover:bg-gray-100 dark:hover:bg-[#2c2c2c] text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200"
              title="放大"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-4 w-4"
                viewBox="0 0 20 20"
                fill="currentColor"
              >
                <path
                  fillRule="evenodd"
                  d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z"
                  clipRule="evenodd"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div
        ref={containerRef}
        className="flex-1 relative bg-white overflow-hidden rounded-b-lg flex items-center justify-center"
        style={{
          cursor: isDragging ? "grabbing" : "grab",
        }}
      >
        <div
          className="bg-white transition-all duration-200 origin-center"
          style={{
            width: String(selectedSize?.width)?.indexOf('%') > -1 ?  `${(Number.parseFloat(String(selectedSize.width)) * ((1 / scale)))}%`  : `${(Number(selectedSize.width) * (1 / scale))}px`,
            height: String(selectedSize?.height)?.indexOf('%') > -1 ?`${(Number.parseFloat(String(selectedSize.height)) * (1 / scale))}%`  : `${(Number(selectedSize.height) * (1 / scale))}px`,
            transform: `scale(${scale})`,
          }}
        >
          <iframe
            ref={iframeRef}
            src={url}
            className="w-full h-full border-none rounded-b-lg bg-white"
            style={{
              width: '100%',
              minHeight: "400px",
            }}
            title="preview"
            sandbox="allow-same-origin allow-scripts allow-popups allow-forms allow-downloads"
            allow="cross-origin-isolated"
            onLoad={() => {
              console.log("Iframe loaded successfully");
              setIframeLoaded(true);
            }}
            onError={(e) => {
              console.error("Iframe failed to load:", e);
              setIframeLoaded(false);
            }}
          />
        </div>
        {isMinPrograme && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-50">
            <div className="text-gray-400">{t("preview.wxminiPreview")}</div>
          </div>
        )}
        {!url && !isMinPrograme && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-50">
            <div className="text-gray-400">{t("preview.noserver")}</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PreviewIframe;
