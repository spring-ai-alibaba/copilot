import React from "react";
import {motion} from "framer-motion";
import {FaSpinner} from "react-icons/fa6";

/**
 * 登录弹窗内部共享的表单控件。
 * 登录弹窗共享控件，统一使用全局语义色。
 */

const inputClass = `
  h-11 w-full rounded-xl border border-border bg-background/70 py-2.5 pl-10 pr-3 text-sm
  text-foreground placeholder:text-muted-foreground/65 outline-none
  transition-[border-color,background-color,box-shadow] duration-150
  hover:bg-background focus:border-foreground/20 focus:ring-2 focus:ring-foreground/[0.06]
`;

type LoginInputProps = React.InputHTMLAttributes<HTMLInputElement> & {
  icon: React.ReactNode;
};

export const LoginInput = ({icon, className, ...props}: LoginInputProps) => (
  <div className="group relative">
    <span
      className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-foreground [&>svg]:h-3.5 [&>svg]:w-3.5"
    >
      {icon}
    </span>
    <input {...props} className={`${inputClass} ${className ?? ""}`} />
  </div>
);

type PrimaryButtonProps = {
  type?: "button" | "submit" | "reset";
  disabled?: boolean;
  onClick?: React.MouseEventHandler<HTMLButtonElement>;
  loading?: boolean;
  loadingText?: string;
  className?: string;
  children?: React.ReactNode;
};

export const PrimaryButton = ({
  type = "button",
  disabled,
  onClick,
  loading,
  loadingText,
  children,
  className,
}: PrimaryButtonProps) => (
  <motion.button
    type={type}
    onClick={onClick}
    disabled={disabled || loading}
    whileHover={{scale: 1.01}}
    whileTap={{scale: 0.98}}
    className={`
      flex h-11 w-full items-center justify-center gap-2 rounded-xl
      bg-foreground text-sm font-medium text-background shadow-sm
      transition-[background-color,opacity,transform] duration-150 hover:bg-foreground/85
      disabled:cursor-not-allowed disabled:opacity-45
      ${className ?? ""}
    `}
  >
    {loading ? (
      <>
        <FaSpinner className="animate-spin" />
        {loadingText}
      </>
    ) : (
      children
    )}
  </motion.button>
);

export const ErrorBanner = ({message}: {message: string}) => (
  <motion.div
    initial={{opacity: 0, y: -4}}
    animate={{opacity: 1, y: 0}}
    transition={{duration: 0.2}}
    className="rounded-xl border border-destructive/25 bg-destructive/[0.06] px-3 py-2 text-xs leading-5 text-destructive"
  >
    {message}
  </motion.div>
);
