import React, {useCallback, useEffect, useRef, useState} from "react";
import {FileIcon, LoaderCircle} from "lucide-react";
import {toast} from "react-toastify";
import {uploadImage} from "@/api/chat";
import classNames from "classnames";
import {useFileStore} from "../../../../WeIde/stores/fileStore";
import type {MentionOption} from "../MentionMenu";
import {ErrorDisplay} from "./ErrorDisplay";
import {ImagePreviewGrid} from "./ImagePreviewGrid";
import {UploadButtons} from "./UploadButtons";
import {SendButton} from "./SendButton";
import type {ChatInputProps as ChatInputPropsType} from "./types";
import {useTranslation} from "react-i18next";
import useChatModeStore from "../../../../../stores/chatModeSlice";
import useThemeStore from "@/stores/themeSlice";
import {v4 as uuidv4} from "uuid";
import OptimizedPromptWord from "./OptimizedPromptWord";

// import type { ModelOption } from './UploadButtons';

export enum ChatMode {
  Chat = "chat",
  Builder = "builder",
}
export const modePlaceholders = {
  [ChatMode.Chat]: "chat.modePlaceholders.chat",
  [ChatMode.Builder]: "chat.modePlaceholders.builder",
};
export const ChatInput: React.FC<ChatInputPropsType> = ({
  input,
  stopRuning,
  isLoading,
  isUploading,
  append,
  uploadedImages,
  setMessages,
  messages,
  handleInputChange,
  handleKeySubmit,
  handleSubmitWithFiles,
  handleFileSelect,
  removeImage,
  addImages,
  setInput,
  setIsUploading,
  handleSketchUpload,
  baseModal,
  setBaseModal,
}) => {
  const { files, errors, removeError } = useFileStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const sketchInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { t } = useTranslation();
  const [showMentionMenu, setShowMentionMenu] = useState(false);
  const [selectedMentionIndex, setSelectedMentionIndex] = useState(0);
  const [mentionPosition, setMentionPosition] = useState({ top: 0, left: 0 });
  const [filteredMentionOptions, setFilteredMentionOptions] = useState<
    MentionOption[]
  >([]);
  const [highlightRange, setHighlightRange] = useState<{
    start: number;
    end: number;
  } | null>(null);
  const [mentions, setMentions] = useState<
    Array<{ start: number; end: number; path: string }>
  >([]);
  const { mode: chatMode } = useChatModeStore();
  const { isDarkMode } = useThemeStore();

  const getFileOptions = () => {
    return Object.entries(files).map(([path]) => ({
      id: path,
      icon: <FileIcon />,
      label: path,
      path: path,
    }));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.nativeEvent.isComposing || e.keyCode === 229) {
      e.preventDefault(); // 阻止默认行为
      return;
    }

    if (e.key === "Backspace" || e.key === "Delete") {
      const cursorPosition = e.currentTarget.selectionStart;
      const mention = mentions.find((m) => m.end === cursorPosition);

      if (mention) {
        e.preventDefault();
        const newValue =
          input.slice(0, mention.start) + input.slice(mention.end);
        const event = {
          target: { value: newValue },
        } as React.ChangeEvent<HTMLTextAreaElement>;
        handleInputChange(event);
        setHighlightRange(null);
        setMentions(mentions.filter((m) => m !== mention));
        return;
      }
    }

    if (e.key === "Enter") {
      setHighlightRange(null);
    }

    if (showMentionMenu) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSelectedMentionIndex((prev) =>
          prev < filteredMentionOptions.length - 1 ? prev + 1 : prev
        );
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setSelectedMentionIndex((prev) => (prev > 0 ? prev - 1 : prev));
      } else if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        if (
          filteredMentionOptions.length > 0 &&
          filteredMentionOptions[selectedMentionIndex]
        ) {
          handleMentionSelect(filteredMentionOptions[selectedMentionIndex]);
        }
      } else if (e.key === "Escape") {
        setShowMentionMenu(false);
      }
    } else {
      handleKeySubmit(e);
    }
  };

  const debounce = (fn: Function, delay: number) => {
    let timer: NodeJS.Timeout;
    return (...args: unknown[]) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), delay);
    };
  };

  const getCursorPosition = (textarea: HTMLTextAreaElement) => {
    const style = window.getComputedStyle(textarea);
    const pos = textarea.selectionStart || 0;

    const div = document.createElement("div");
    div.style.position = "absolute";
    div.style.visibility = "hidden";
    div.style.whiteSpace = "pre-wrap";
    div.style.wordWrap = "break-word";
    div.style.width = style.width;
    div.style.padding = style.padding;
    div.style.font = style.font;
    div.style.lineHeight = style.lineHeight;

    const textBeforeCursor = textarea.value.substring(0, pos);
    const textAfterCursor = textarea.value.substring(pos);

    const beforeNode = document.createTextNode(textBeforeCursor);
    div.appendChild(beforeNode);

    const cursorNode = document.createElement("span");
    cursorNode.textContent = "|";
    div.appendChild(cursorNode);

    const afterNode = document.createTextNode(textAfterCursor);
    div.appendChild(afterNode);

    document.body.appendChild(div);

    const cursorRect = cursorNode.getBoundingClientRect();
    const textareaRect = textarea.getBoundingClientRect();

    document.body.removeChild(div);

    const relativeTop = cursorRect.top - textareaRect.top + textarea.scrollTop;
    const relativeLeft = cursorRect.left - textareaRect.left;

    const maxTop = textarea.offsetHeight - 200;
    const adjustedTop = Math.min(relativeTop, maxTop);

    return {
      left: relativeLeft,
      top: adjustedTop,
      height: parseFloat(style.lineHeight) || 20,
    };
  };

  const updateMentionPosition = useCallback(() => {
    if (!showMentionMenu || !textareaRef.current) return;

    const textarea = textareaRef.current;
    const { left, top, height } = getCursorPosition(textarea);
    const textareaRect = textarea.getBoundingClientRect();
    const menuWidth = 200;

    const adjustedLeft = Math.min(left, textareaRect.width - menuWidth - 10);

    setMentionPosition({
      top: top + height,
      left: adjustedLeft,
    });
  }, [showMentionMenu]);

  const onInputChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const newValue = e.target.value;
      const oldValue = input;
      handleInputChange(e);

      if (newValue.length < oldValue.length) {
        const deletedStart = e.target.selectionStart;
        const mention = mentions.find(
          (m) => deletedStart > m.start && deletedStart < m.end
        );

        if (mention) {
          e.preventDefault();
          return;
        }

        const diff = oldValue.length - newValue.length;
        const updatedMentions = mentions.map((m) => {
          if (m.start > deletedStart) {
            return {
              ...m,
              start: m.start - diff,
              end: m.end - diff,
            };
          }
          return m;
        });
        setMentions(updatedMentions);
      }

      const lastAtIndex = newValue.lastIndexOf("@");
      const textAfterLastAt = newValue.substring(lastAtIndex + 1);

      if (lastAtIndex !== -1 && !textAfterLastAt.includes(" ")) {
        const searchTerm = textAfterLastAt.toLowerCase();
        const fileOptions = getFileOptions();
        const filteredOptions = fileOptions.filter(
          (option) =>
            option.label.toLowerCase().includes(searchTerm) ||
            option.path?.toLowerCase().includes(searchTerm)
        );

        if (filteredOptions.length > 0) {
          updateMentionPosition();
          setFilteredMentionOptions(filteredOptions);
          setShowMentionMenu(true);
          setSelectedMentionIndex(0);
        } else {
          setShowMentionMenu(false);
        }
      } else {
        setShowMentionMenu(false);
      }
    },
    [input, mentions, handleInputChange, updateMentionPosition]
  );

  const handleMentionSelect = (option: MentionOption) => {
    const textarea = textareaRef.current;
    if (!textarea || !option.path) return;

    const cursorPosition = textarea.selectionEnd;
    const textBeforeCursor = textarea.value.substring(0, cursorPosition);
    const textAfterCursor = textarea.value.substring(cursorPosition);

    const lastAtIndex = textBeforeCursor.lastIndexOf("@");
    if (lastAtIndex === -1) return;

    const mentionText = `@${option.path} `;
    const newValue =
      textBeforeCursor.substring(0, lastAtIndex) +
      mentionText +
      textAfterCursor;

    const newMention = {
      start: lastAtIndex,
      end: lastAtIndex + mentionText.length,
      path: option.path,
    };
    setMentions([...mentions, newMention]);

    setHighlightRange({
      start: lastAtIndex,
      end: lastAtIndex + (option.path?.length || 0) + 1,
    });

    const event = {
      target: { value: newValue },
    } as React.ChangeEvent<HTMLTextAreaElement>;

    handleInputChange(event);
    setShowMentionMenu(false);
  };

  const handlePaste = async (e: ClipboardEvent) => {
    console.log(baseModal, "useImage");
    if (!baseModal.useImage) return;
    if (isUploading) return;

    const items = e.clipboardData?.items;
    if (!items) return;

    const imageItems = Array.from(items).filter(
      (item) => item.type.indexOf("image") !== -1
    );

    if (imageItems.length > 0) {
      e.preventDefault();
      setIsUploading(true);

      try {
        const uploadResults = await Promise.all(
          imageItems.map(async (item) => {
            const file = item.getAsFile();
            if (!file) throw new Error("Failed to get file from clipboard");

            const url = await uploadImage(file);
            return {
              id: uuidv4(),
              file,
              url,
              localUrl: URL.createObjectURL(file),
              status: "done" as const,
            };
          })
        );

        addImages(uploadResults);

        if (uploadResults.length === 1) {
          toast.success("Image pasted successfully");
        } else {
          toast.success(`${uploadResults.length} images pasted successfully`);
        }
      } catch (error) {
        console.error("Failed to upload pasted images:", error);
        toast.error("Failed to upload pasted images");
      } finally {
        setIsUploading(false);
      }
    }
  };

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    textarea.addEventListener("paste", handlePaste);
    return () => {
      textarea.removeEventListener("paste", handlePaste);
    };
  }, [isUploading, baseModal?.name]);

  useEffect(() => {
    if (showMentionMenu) {
      updateMentionPosition();
    }
  }, [input, showMentionMenu, updateMentionPosition]);

  useEffect(() => {
    const handleResize = debounce(() => {
      if (showMentionMenu) {
        updateMentionPosition();
      }
    }, 100);

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [showMentionMenu, updateMentionPosition]);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "0px";
    textarea.style.height = `${Math.min(200, Math.max(72, textarea.scrollHeight))}px`;
  }, [input]);

  return (
    <div className="shrink-0 px-3 pb-4 pt-2 sm:px-5">
      <div className="mx-auto w-full max-w-[760px]">
        <ErrorDisplay
          errors={errors}
          onAttemptFix={async (error, index) => {
            const errorText = `Please help me fix this error:\n${error.code}`;
            handleSubmitWithFiles(null, errorText);
            removeError(index);
          }}
          onRemoveError={removeError}
        />

        <div className="mb-1.5 flex items-center">
          <OptimizedPromptWord input={input} setInput={setInput} />
        </div>

        <div className="arc-composer-card relative overflow-visible">
          <div
            className={classNames(
              "relative",
              isUploading && "opacity-50 pointer-events-none"
            )}
          >
            {isUploading && (
              <div className="absolute inset-0 z-40 flex items-center justify-center rounded-[inherit] bg-background/65 backdrop-blur-sm">
                <div className="flex items-center gap-2 rounded-full border border-border bg-popover px-3 py-1.5 text-xs text-muted-foreground shadow-lg">
                  <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                  正在上传附件
                </div>
              </div>
            )}

            {uploadedImages.length ? (
              <div className="px-3 pt-3">
                <ImagePreviewGrid uploadedImages={uploadedImages} onRemoveImage={removeImage} />
              </div>
            ) : null}

            <div className="relative">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={onInputChange}
                onKeyDown={handleKeyDown}
                placeholder={t(
                  chatMode === ChatMode.Chat
                    ? modePlaceholders[ChatMode.Chat]
                    : modePlaceholders[ChatMode.Builder],
                )}
                className={classNames(
                  "relative z-10 block w-full resize-none overflow-y-auto bg-transparent px-4 pb-2 pt-3.5 text-[14px] leading-6 text-foreground outline-none",
                  "placeholder:text-muted-foreground/70",
                  "[scrollbar-width:thin]",
                )}
                rows={2}
                style={{
                  minHeight: "72px",
                  maxHeight: "200px",
                  caretColor: isDarkMode ? "white" : "black",
                }}
              />

              {highlightRange && (
                <div
                  className="pointer-events-none absolute inset-0 px-4 pb-2 pt-3.5 text-[14px] leading-6"
                  style={{
                    fontFamily: "inherit",
                    lineHeight: "inherit",
                    overflow: "hidden",
                  }}
                >
                  <span className="invisible">
                    {input.substring(0, highlightRange.start)}
                  </span>
                  <span className="rounded bg-foreground/10 text-transparent">
                    {input.substring(highlightRange.start, highlightRange.end)}
                  </span>
                  <span className="invisible">
                    {input.substring(highlightRange.end)}
                  </span>
                </div>
              )}
            </div>

            {showMentionMenu && (
              <div
                className="absolute z-50 transition-all duration-100"
                style={{
                  top: `${mentionPosition.top + 22}px`,
                  left: `${mentionPosition.left + 16}px`,
                  maxHeight: "200px",
                  width: "240px",
                }}
              >
                <div className="arc-popover overflow-hidden p-1.5">
                  <div className="max-h-[180px] overflow-y-auto">
                    {filteredMentionOptions.map((option, index) => (
                      <button
                        type="button"
                        key={option.id}
                        className={classNames(
                          "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs transition-colors",
                          selectedMentionIndex === index
                            ? "bg-foreground/[0.075] text-foreground"
                            : "text-muted-foreground hover:bg-foreground/[0.045] hover:text-foreground"
                        )}
                        onClick={() => {
                          handleMentionSelect(option);
                        }}
                        ref={
                          index === selectedMentionIndex
                            ? (el) => {
                                if (el) {
                                  el.scrollIntoView({ block: "nearest" });
                                }
                              }
                            : null
                        }
                      >
                        <span className="[&>svg]:h-3.5 [&>svg]:w-3.5">{option.icon}</span>
                        <span className="truncate">{option.path}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}
            <div className="flex items-center justify-between gap-3 px-2.5 pb-2.5 pt-1">
              <div className="flex min-w-0 items-center">
                <UploadButtons
                  isLoading={isLoading}
                  isUploading={isUploading}
                  baseModal={baseModal}
                  setMessages={setMessages}
                  append={append}
                  messages={messages}
                  setBaseModal={setBaseModal}
                  handleSubmitWithFiles={handleSubmitWithFiles}
                  onImageClick={() => fileInputRef.current?.click()}
                  onSketchClick={() => sketchInputRef.current?.click()}
                />
              </div>

              <div className="flex shrink-0 items-center gap-2">
                <span className="hidden text-[10px] text-muted-foreground/65 sm:inline">
                  {t("chat.buttons.composerHint", {
                    defaultValue: "Enter 发送 · Shift + Enter 换行",
                  })}
                </span>
                <SendButton
                  isLoading={isLoading}
                  stop={stopRuning}
                  isUploading={isUploading}
                  hasInput={
                    Boolean(input?.trim()) || uploadedImages.some((image) => image.status === "done")
                  }
                  hasUploadingImages={uploadedImages.some((image) => image.status === "uploading")}
                  onClick={handleSubmitWithFiles}
                />
              </div>
            </div>
          </div>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          onChange={handleFileSelect}
          className="hidden"
          multiple
          accept="image/*"
        />
        <input
          ref={sketchInputRef}
          type="file"
          onChange={handleSketchUpload}
          className="hidden"
          accept=".sketch"
        />
      </div>
    </div>
  );
};
