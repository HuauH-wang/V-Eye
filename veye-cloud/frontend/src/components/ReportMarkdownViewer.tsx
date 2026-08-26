import ReactMarkdown, { defaultUrlTransform } from "react-markdown";
import remarkGfm from "remark-gfm";
import RecordImage from "./RecordImage";

const VEYE_IMAGE_PREFIX = "veye://image/";

type Props = {
  markdown: string;
  className?: string;
};

function reportUrlTransform(url: string): string {
  if (url.startsWith(VEYE_IMAGE_PREFIX)) return url;
  return defaultUrlTransform(url);
}

function MarkdownImage({
  src,
  alt,
}: {
  src?: string;
  alt?: string;
}) {
  if (src?.startsWith(VEYE_IMAGE_PREFIX)) {
    const requestId = src.slice(VEYE_IMAGE_PREFIX.length);
    if (requestId) {
      return (
        <figure className="report-md-figure">
          <RecordImage
            requestId={requestId}
            alt={alt ?? "识图记录"}
            className="report-md-img"
            caption={alt}
          />
        </figure>
      );
    }
  }
  if (!src) return null;
  return <img src={src} alt={alt ?? ""} className="report-md-img" loading="lazy" />;
}

export default function ReportMarkdownViewer({ markdown, className }: Props) {
  return (
    <div className={`report-markdown${className ? ` ${className}` : ""}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        urlTransform={reportUrlTransform}
        components={{
          img: ({ src, alt }) => <MarkdownImage src={src} alt={alt} />,
        }}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  );
}
