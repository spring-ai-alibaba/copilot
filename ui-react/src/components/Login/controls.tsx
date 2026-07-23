import React from "react";
import {motion} from "framer-motion";
import {FaSpinner} from "react-icons/fa6";

/**
 * 登录弹窗内部共享的表单控件。
 * 视觉规范与 Settings / Header 保持一致：
 *  - 卡片/输入框圆角 rounded-lg
 *  - 暗色：#222 输入框、#333 边框、#666 次级文字
 *  - 亮色：white 输入框、gray-200 边框、gray-400 次级文字
 *  - 聚焦/强调色：#3B82F6
 *  - 主按钮：品牌渐变 blue-500 → purple-500
 */

const inputClass = `
  w-full rounded-lg border border-gray-200 dark:border-[#333]
  bg-white dark:bg-[#222] py-3 pl-11 pr-4 text-sm
  text-gray-900 dark:text-white
  placeholder:text-gray-400 dark:placeholder:text-[#666]
  focus:outline-none focus:border-[#3B82F6] focus:ring-1 focus:ring-[#3B82F6]
  transition-all duration-200
`;

type LoginInputProps = React.InputHTMLAttributes<HTMLInputElement> & {
  icon: React.ReactNode;
};

export const LoginInput = ({icon, className, ...props}: LoginInputProps) => (
  <div className="group relative">
    <span
      className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2
        text-gray-400 dark:text-[#666] transition-colors duration-200
        group-focus-within:text-[#3B82F6]"
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
      flex w-full items-center justify-center gap-2 rounded-lg py-3
      bg-gradient-to-r from-blue-500 to-purple-500
      text-sm font-medium text-white shadow-lg shadow-blue-500/25
      transition-colors duration-200
      hover:from-blue-600 hover:to-purple-600
      disabled:cursor-not-allowed disabled:opacity-60 disabled:shadow-none
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
    className="rounded-lg border border-red-200 bg-red-50 px-3 py-2
      text-sm text-red-500
      dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-400"
  >
    {message}
  </motion.div>
);
