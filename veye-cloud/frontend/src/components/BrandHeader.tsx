import VEyeLogo from "./VEyeLogo";

type Props = {
  className?: string;
};

export default function BrandHeader({ className = "" }: Props) {
  return (
    <div className={`brand ${className}`.trim()}>
      <VEyeLogo size={42} className="brand-logo" />
      <div>
        <strong>V-EYE</strong>
        <span className="brand-sub">Natural Observatory</span>
      </div>
    </div>
  );
}
