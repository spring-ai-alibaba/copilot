import { FilePreview } from "@/stores/chatSlice";

export const ImagePreview = ({
  file,
  onRemove,
}: {
  file: FilePreview;
  onRemove: () => void;
}) => {
  return (
    <div className="relative group">
      <div className="w-20 h-20 relative">
        <img
          src={file.localUrl}
          alt="Preview"
          className={`w-20 h-20 object-cover rounded border border-gray-600/30 ${
            file.status === "uploading" ? "opacity-50" : ""
          }`}
        />
        {file.status === "uploading" && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={onRemove}
        className="absolute -top-2 -right-2 bg-red-400 text-white rounded-full w-5 h-5 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
        disabled={file.status === "uploading"}
      >
        ×
      </button>
    </div>
  );
};

interface ImageGridProps {
  images: Array<{
    url: string;
    localUrl: string;
    // ... 其他属性
  }>;
  onImageClick?: (url: string) => void;
}

export const ImageGrid: React.FC<ImageGridProps> = ({
  images,
  onImageClick,
}) => {
  return (
    <div className="flex flex-wrap gap-2">
      {images.map((image, index) => (
        <div
          key={index}
          className="relative group cursor-pointer"
          onClick={() => onImageClick?.(image.localUrl)}
        >
          <img
            src={image.localUrl}
            alt={`Attachment ${index + 1}`}
            className="max-w-[200px] max-h-[200px] rounded-lg object-cover hover:opacity-90 transition-opacity"
          />
          <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors rounded-lg" />
        </div>
      ))}
    </div>
  );
};
