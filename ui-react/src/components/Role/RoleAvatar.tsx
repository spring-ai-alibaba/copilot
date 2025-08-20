import React from "react";

interface RoleAvatarProps {
  type: "leader" | "manager" | "architect" | "engineer" | "analyst";
}

export const RoleAvatar: React.FC<RoleAvatarProps> = ({ type }) => {
  const getAvatarSvg = () => {
    switch (type) {
      case "leader":
        return (
          <svg
            width="40"
            height="40"
            viewBox="0 0 40 40"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M20 40c11.046 0 20-8.954 20-20S31.046 0 20 0 0 8.954 0 20s8.954 20 20 20z"
              fill="#FFB156"
            />
            <path
              d="M20 28c4.97 0 9-4.03 9-9s-4.03-9-9-9-9 4.03-9 9 4.03 9 9 9z"
              fill="#FFF5E9"
            />
          </svg>
        );
      case "manager":
        return (
          <svg
            width="40"
            height="40"
            viewBox="0 0 40 40"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M20 40c11.046 0 20-8.954 20-20S31.046 0 20 0 0 8.954 0 20s8.954 20 20 20z"
              fill="#A78BFA"
            />
            <path
              d="M20 28c4.97 0 9-4.03 9-9s-4.03-9-9-9-9 4.03-9 9 4.03 9 9 9z"
              fill="#F3F0FF"
            />
          </svg>
        );
      case "architect":
        return (
          <svg
            width="40"
            height="40"
            viewBox="0 0 40 40"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M20 40c11.046 0 20-8.954 20-20S31.046 0 20 0 0 8.954 0 20s8.954 20 20 20z"
              fill="#60A5FA"
            />
            <path
              d="M20 28c4.97 0 9-4.03 9-9s-4.03-9-9-9-9 4.03-9 9 4.03 9 9 9z"
              fill="#EFF6FF"
            />
          </svg>
        );
      case "engineer":
        return (
          <svg
            width="40"
            height="40"
            viewBox="0 0 40 40"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M20 40c11.046 0 20-8.954 20-20S31.046 0 20 0 0 8.954 0 20s8.954 20 20 20z"
              fill="#4ADE80"
            />
            <path
              d="M20 28c4.97 0 9-4.03 9-9s-4.03-9-9-9-9 4.03-9 9 4.03 9 9 9z"
              fill="#ECFDF5"
            />
          </svg>
        );
      case "analyst":
        return (
          <svg
            width="40"
            height="40"
            viewBox="0 0 40 40"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M20 40c11.046 0 20-8.954 20-20S31.046 0 20 0 0 8.954 0 20s8.954 20 20 20z"
              fill="#94A3B8"
            />
            <path
              d="M20 28c4.97 0 9-4.03 9-9s-4.03-9-9-9-9 4.03-9 9 4.03 9 9 9z"
              fill="#F1F5F9"
            />
          </svg>
        );
    }
  };

  return <div className="w-full h-full">{getAvatarSvg()}</div>;
};
