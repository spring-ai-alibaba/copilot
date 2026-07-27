import React, {useState} from "react";
import {AlertTriangle} from "lucide-react";
import classNames from "classnames";
import {useTranslation} from "react-i18next";
import type {ErrorDisplayProps} from "./types";

export const ErrorDisplay: React.FC<ErrorDisplayProps> = ({
  errors,
  onAttemptFix,
  onRemoveError,
}) => {
  const {t} = useTranslation();
  const [expandedErrors, setExpandedErrors] = useState<Set<number>>(new Set());
  const [showProblems, setShowProblems] = useState<Set<number>>(new Set());

  const toggleErrorExpanded = (index: number) => {
    setExpandedErrors(prev => {
      const newSet = new Set(prev);
      if (newSet.has(index)) {
        newSet.delete(index);
      } else {
        newSet.add(index);
      }
      return newSet;
    });
  };

  const toggleProblemVisible = (index: number) => {
    setShowProblems(prev => {
      const newSet = new Set(prev);
      if (newSet.has(index)) {
        newSet.delete(index);
      } else {
        newSet.add(index);
      }
      return newSet;
    });
  };

  return (
    <div className="max-h-[40vh] overflow-y-auto">
      {errors.map((error, index) => (
        <div
          key={index}
          className={classNames(
            "mb-1.5 cursor-pointer rounded-xl border bg-popover/95 shadow-lg transition-all duration-200",
            error.severity === "error" ? "border-destructive/30" : "border-amber-500/30",
            expandedErrors.has(index) ? "p-3" : "p-1.5"
          )}
        >
          <div
            className="flex items-center justify-between"
            onClick={() => toggleErrorExpanded(index)}
          >
            <div className="flex items-center gap-1.5 text-destructive">
              <AlertTriangle className="w-3.5 h-3.5" />
              <span className="font-medium text-xs">{error.message}</span>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                toggleErrorExpanded(index);
              }}
              className="p-0.5 text-muted-foreground hover:text-foreground"
            >
              <svg
                className={classNames(
                  "w-3.5 h-3.5 transition-transform duration-300",
                  expandedErrors.has(index) ? "transform rotate-180" : ""
                )}
                viewBox="0 0 20 20"
                fill="none"
                stroke="currentColor"
              >
                <path
                  d="M6 8l4 4 4-4"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>
          </div>

          <div
            className={classNames(
              "overflow-hidden transition-all duration-300 ease-in-out",
              expandedErrors.has(index)
                ? "max-h-[500px] opacity-100 mt-2"
                : "max-h-0 opacity-0"
            )}
          >
            <div className="transform transition-transform duration-300 ease-in-out">
              {expandedErrors.has(index) && (
                <>
                  <button
                    className="flex w-full items-center justify-between rounded-lg bg-muted/65 p-2 text-left text-xs"
                    onClick={() => toggleProblemVisible(index)}
                  >
                    <div className="flex items-center gap-1.5">
                      <span className="flex h-4 w-4 items-center justify-center rounded-full bg-foreground text-[10px] text-background">
                        {error.number || 1}
                      </span>
                      <span className="text-foreground">
                        {t("chat.errors.view_problem", {defaultValue: "查看问题"})}
                      </span>
                    </div>
                    <svg
                      className={classNames(
                        "h-3.5 w-3.5 text-muted-foreground transition-transform duration-300",
                        showProblems.has(index)
                          ? "transform rotate-180"
                          : ""
                      )}
                      viewBox="0 0 20 20"
                      fill="none"
                      stroke="currentColor"
                    >
                      <path
                        d="M6 8l4 4 4-4"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </button>

                  <div
                    className={classNames(
                      "overflow-hidden transition-all duration-300 ease-in-out",
                      showProblems.has(index)
                        ? "max-h-[500px] opacity-100 mt-1.5"
                        : "max-h-0 opacity-0"
                    )}
                  >
                    {showProblems.has(index) && (
                      <div className="mt-1.5 rounded-lg border border-border/70 bg-muted/35 p-2 text-xs">
                        <div className="flex items-start gap-2">
                          <div className="mt-0.5 text-destructive">
                            <AlertTriangle className="w-3.5 h-3.5" />
                          </div>
                          <div className="flex-1">
                            <p className="mb-1 text-foreground/85">
                              Error code: {error.code}
                            </p>
                            <pre className="whitespace-pre-wrap break-words rounded bg-muted p-1.5 font-mono text-muted-foreground">
                              <code>{error.message}</code>
                            </pre>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="mt-2">
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => onAttemptFix(error, index)}
                        className="rounded-lg bg-foreground px-2.5 py-1.5 text-xs text-background transition-colors hover:bg-foreground/85"
                      >
                        尝试修复
                      </button>
                      <button
                        onClick={() => onRemoveError(index)}
                        className="rounded-lg bg-muted px-2.5 py-1.5 text-xs text-foreground transition-colors hover:bg-muted/75"
                      >
                        清除
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
};
