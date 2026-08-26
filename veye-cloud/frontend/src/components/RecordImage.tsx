import ClickableImage from "./ClickableImage";
import { useAuthenticatedImage } from "../hooks/useAuthenticatedImage";

type Props = {
  requestId: string;
  alt: string;
  className?: string;
  refreshKey?: string | number;
  caption?: string;
};

export default function RecordImage({
  requestId,
  alt,
  className,
  refreshKey = 0,
  caption,
}: Props) {
  const path = `/vision/records/${requestId}/image`;
  const { url, failed, loading, retry } = useAuthenticatedImage(path, refreshKey);

  return (
    <ClickableImage
      src={url}
      alt={alt}
      className={className}
      caption={caption ?? alt}
      loading={loading}
      failed={failed}
      onRetry={retry}
    />
  );
}
