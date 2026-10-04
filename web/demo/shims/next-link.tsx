import { forwardRef } from "react";
import { navigate } from "./router";

type Props = Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
  href: string;
  replace?: boolean;
};

const Link = forwardRef<HTMLAnchorElement, Props>(function Link({ href, replace, onClick, ...props }, ref) {
  if (/^https?:/.test(href))
    return <a ref={ref} href={href} target="_blank" rel="noreferrer" onClick={onClick} {...props} />;
  return (
    <a
      ref={ref}
      href={`#${href}`}
      onClick={(event) => {
        onClick?.(event);
        if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey) return;
        event.preventDefault();
        navigate(href, replace);
      }}
      {...props}
    />
  );
});

export default Link;
