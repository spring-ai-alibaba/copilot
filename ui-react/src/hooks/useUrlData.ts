import { useState, useEffect, useRef } from 'react';
import { parseDataFromUrl } from '../utils/parseDataFromUrl';
import { ChatRequestOptions, CreateMessage, Message } from 'ai';
import { v4 as uuidv4 } from "uuid";
import { UploadRef } from 'antd/es/upload/Upload';

/**
 * Interface definition for URL data
 */
export interface UrlData {
  text: string;
  images: string[];
  type: string;
}

interface UseUrlDataReturn {
  status: boolean;
  type: string;
  text: string;
}

interface Iprops {
  append?: (
    message: Message | CreateMessage,
    chatRequestOptions?: ChatRequestOptions
  ) => void;
  handleGetFile?: (file: File) => void;
  uploadRef?: React.RefObject<UploadRef>;
}

let isFinishUseUrlData = false;
/**
 * Custom Hook for parsing and managing URL data
 * @returns {UseUrlDataReturn} Object containing data, loading state, and error information
 */
export function useUrlData(props?: Iprops): UseUrlDataReturn {
  const [data, setData] = useState<UrlData>({ text: '', images: [], type: '' });
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);
  const { append, handleGetFile } = props || {};
  const typeRef = useRef<string>('');
  const loadingRef = useRef<boolean>(false);

  useEffect(() => {

    const parsedData = parseDataFromUrl();
    const { type } = parsedData;
    typeRef.current = type;
    if (!isFinishUseUrlData) {
      if (type === "chat") {
        fetchData();
        loadingRef.current = true;
      } else if (type === "sketch") {
        loadingRef.current = true;
      }
      isFinishUseUrlData = true;
    }

  }, []);


  const fetchData = async () => {
    try {
      const parsedData = parseDataFromUrl();
      const { text, images } = parsedData;

      if (!text) {
        setIsLoading(false);
        return;
      }

      const currentAttachments = await Promise.all(
        images.map(async (imageUrl) => {
          // const imgBlob = await getImageBlobFromUrl(imageUrl);
          // const file = new File([imgBlob], "sketch-image.png", {
          //   type: "image/png",
          // });
          const id = uuidv4();
          return {
            id,
            name: id,
            type: "image",
            localUrl: imageUrl,
            contentType: "image/png",
            url: imageUrl,
          };
        })
      );

      append(
        {
          role: "user",
          content: text,
        },
        {
          experimental_attachments: currentAttachments,
        }
      );

      setData(parsedData);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to parse URL data'));
    } finally {
      setIsLoading(false);
    }
  };

  return { status: loadingRef.current, type: typeRef.current, text: parseDataFromUrl().text };
}

/**
 * Get Blob object from image URL
 * @param imageUrl URL address of the image
 * @returns Promise<Blob> Returns the image's Blob object
 */
async function getImageBlobFromUrl(imageUrl: string): Promise<Blob> {
  try {
    // Initiate fetch request to get the image
    const response = await fetch(imageUrl, {
      method: 'GET',
      // Add CORS support
      mode: 'cors',
    });

    // Check if request was successful
    if (!response.ok) {
      throw new Error(`Failed to fetch image`);
    }

    // Convert response to Blob object
    const imageBlob = await response.blob();
    return imageBlob;
  } catch (error) {
    console.error('Error getting image Blob:', error);
    throw error;
  }
}