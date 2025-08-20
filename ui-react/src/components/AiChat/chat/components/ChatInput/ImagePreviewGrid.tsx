import React from "react";
import { ImagePreview } from "../ImageGrid";
import type { ImagePreviewGridProps } from "./types";

export const ImagePreviewGrid: React.FC<ImagePreviewGridProps> = ({
  uploadedImages,
  onRemoveImage,
}) => {
  if (uploadedImages.length === 0) return null;
  
  return (
    <div className="mb-4 flex flex-wrap gap-2">
      {uploadedImages.map((file) => (
        <ImagePreview
          key={file.id}
          file={file}
          onRemove={() => onRemoveImage(file.id)}
        />
      ))}
    </div>
  );
}; 